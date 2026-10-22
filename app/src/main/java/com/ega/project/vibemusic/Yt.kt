package com.ega.project.vibemusic
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/*
 * MODE STREAMING YOUTUBE
 * Memakai NewPipeExtractor (tidak resmi). Bisa berhenti bekerja kapan saja saat YouTube mengubah sistemnya;
 * perbaikannya biasanya cukup menaikkan versi NewPipeExtractor di app/build.gradle.kts.
 * Memakai ekstraktor ini melanggar syarat layanan YouTube dan tidak cocok untuk dirilis di Play Store.
 */

/** Downloader berbasis OkHttp yang dibutuhkan NewPipeExtractor. */
class NpDownloader:Downloader(){
 private val client=OkHttpClient.Builder().readTimeout(30,TimeUnit.SECONDS).build()
 override fun execute(request:Request):Response{
  val body=request.dataToSend()?.toRequestBody()
  val rb=okhttp3.Request.Builder().method(request.httpMethod(),body).url(request.url()).addHeader("User-Agent",Yt.UA)
  for((name,values) in request.headers()){
   if(values.size>1){rb.removeHeader(name);values.forEach{rb.addHeader(name,it)}}
   else if(values.size==1)rb.header(name,values[0])
  }
  val r=client.newCall(rb.build()).execute()
  if(r.code==429){r.close();throw ReCaptchaException("reCAPTCHA diminta",request.url())}
  val text=r.body?.string()
  return Response(r.code,r.message,r.headers.toMultimap(),text,r.request.url.toString())
 }
}

/** Lagu online yang dipilih pengguna (diputar / masuk antrean / masuk playlist) disimpan agar bisa dipulihkan. */
object Online{
 private var sp:SharedPreferences?=null
 /** hasil pencarian yang pernah tampil (belum tentu disimpan) */
 private val seen=ConcurrentHashMap<Long,Song>()
 val saved=mutableStateMapOf<Long,Song>()
 var version by mutableIntStateOf(0)
 fun init(c:Context){
  if(sp!=null)return
  sp=c.applicationContext.getSharedPreferences("yt",0)
  runCatching{
   val a=JSONArray(sp!!.getString("songs","[]"))
   for(i in 0 until a.length()){
    val o=a.getJSONObject(i)
    val w=o.getString("w")
    saved[ytId(w)]=Song(ytId(w),o.getString("t"),o.getString("a"),UB,UG,o.getLong("d"),null,w,o.optString("th").ifBlank{null})
   }
  }
 }
 fun see(l:List<Song>){l.forEach{seen[it.id]=it}}
 fun persist(id:Long){
  val s=seen[id]?:return
  if(saved.containsKey(id))return
  saved[id]=s;save();version++
 }
 fun persist(s:Song){seen[s.id]=s;persist(s.id)}
 private fun save(){
  val a=JSONArray()
  saved.values.forEach{s->a.put(JSONObject().put("w",s.web).put("t",s.title).put("a",s.artist).put("d",s.dur).put("th",s.thumb?:""))}
  sp?.edit()?.putString("songs",a.toString())?.apply()
 }
}

/** Playlist hasil pencarian YouTube */
data class YtPl(val title:String,val by:String,val url:String,val thumb:String?){
 /** dipakai agar bisa memakai komponen Cover/Tile yang menerima Song */
 fun asSong()=Song(ytId("pl:$url"),title,by,UB,UG,0L,null,null,thumb)
}
/** Daftar YouTube di Home. Ubah [q] untuk mengganti isi; [playlist]=true mengambil playlist teratas dari hasil pencarian. */
class YtSection(val title:String,val q:String,val playlist:Boolean)

/** jumlah lagu yang ditampilkan di daftar Trending */
const val YT_TRENDING_SIZE=50

/** detail stream audio yang sedang dipakai untuk satu video (ditampilkan di kartu info lagu) */
data class StreamMeta(val format:String,val mime:String?,val kbps:Int,val codec:String?,val hz:Int,val max:Boolean)

object Yt{
 const val UA="Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
 val TRENDING=YtSection("Trending","Trending music Indonesia",true)
 private val ready by lazy{NewPipe.init(NpDownloader(),Localization("id","ID"),ContentCountry("ID"));true}
 private val svc get()=ServiceList.YouTube
 private val idRe=Regex("""(?:v=|youtu\.be/|/shorts/)([\w-]{11})""")

 private fun song(it:StreamInfoItem):Song?{
  val vid=idRe.find(it.url)?.groupValues?.get(1)?:return null
  if(it.duration<=0)return null   // lewati siaran langsung / tanpa durasi
  val artist=it.uploaderName?.removeSuffix(" - Topic")?.takeIf{a->a.isNotBlank()}?:UA
  return Song(ytId(vid),it.name,artist,UB,UG,it.duration*1000,null,vid,it.thumbnails.lastOrNull()?.url)
 }

 suspend fun songs(q:String):List<Song> = withContext(Dispatchers.IO){
  ready
  val qh=svc.searchQHFactory.fromQuery(q,listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),"")
  SearchInfo.getInfo(svc,qh).relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull{song(it)}.also{Online.see(it)}
 }

 suspend fun playlists(q:String):List<YtPl> = withContext(Dispatchers.IO){
  ready
  val qh=svc.searchQHFactory.fromQuery(q,listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS),"")
  SearchInfo.getInfo(svc,qh).relatedItems.filterIsInstance<PlaylistInfoItem>()
   .map{YtPl(it.name,it.uploaderName?:"",it.url,it.thumbnails.lastOrNull()?.url)}
 }

 /** isi playlist (maksimal ± 300 lagu: halaman pertama + 2 halaman berikutnya) */
 suspend fun playlistSongs(url:String):List<Song> = withContext(Dispatchers.IO){
  ready
  val info=PlaylistInfo.getInfo(svc,url)
  val out=ArrayList<StreamInfoItem>(info.relatedItems)
  var page=info.nextPage;var n=0
  while(page!=null&&n<2){
   val more=PlaylistInfo.getMoreItems(svc,url,page)
   out+=more.items;page=more.nextPage;n++
  }
  out.mapNotNull{song(it)}.also{Online.see(it)}
 }

 /**
  * isi satu daftar (maksimal [YT_TRENDING_SIZE] lagu): playlist teratas dari hasil pencarian;
  * bila playlist tidak ditemukan atau lagunya kurang, dilengkapi dari pencarian lagu biasa.
  */
 suspend fun section(s:YtSection):List<Song>{
  val out=LinkedHashMap<Long,Song>()
  if(s.playlist){
   val p=runCatching{playlists(s.q)}.getOrDefault(emptyList()).firstOrNull()
   if(p!=null)runCatching{playlistSongs(p.url)}.getOrDefault(emptyList()).forEach{out.putIfAbsent(it.id,it)}
  }
  if(out.size<YT_TRENDING_SIZE){
   val r=runCatching{songs(s.q)}
   r.getOrNull()?.forEach{out.putIfAbsent(it.id,it)}
   if(out.isEmpty())throw r.exceptionOrNull()?:IOException("Tidak ada lagu")
  }
  return out.values.take(YT_TRENDING_SIZE)
 }

 private val junk=Regex("""\s*[(\[][^)\]]*(official|video|audio|lyric|lirik|\bmv\b|m/v|\bhd\b|4k|visuali[sz]er|clip)[^)\]]*[)\]]""",RegexOption.IGNORE_CASE)
 /** judul YouTube -> judul bersih untuk pencarian lirik: buang "(Official Video)", "[Lyric]", awalan "Artis - " dll. */
 fun cleanTitle(t:String,artist:String):String{
  var r=t.replace(junk,"").substringBefore(" | ").trim()
  if(r.startsWith("$artist - ",true))r=r.substring(artist.length+3).trim()
  return r.ifBlank{t}
 }

 private val urls=ConcurrentHashMap<String,Pair<String,Long>>()
 @Volatile private var sp:SharedPreferences?=null
 private val metas=ConcurrentHashMap<String,StreamMeta>()
 private fun qKey(video:String,max:Boolean)="$video|${if(max)"max" else "std"}"
 /** detail stream yang dipakai (sesuai pengaturan kualitas saat ini); null bila lagu belum pernah dibuka */
 fun streamMeta(video:String):StreamMeta?=metas[qKey(video,sp?.getBoolean("yt_max_quality",false)==true)]
 /** dibaca dari prefs "AppState" (kunci yt_max_quality) setiap kali url dibuka, jadi perubahan di pengaturan langsung berlaku untuk lagu berikutnya */
 fun init(c:Context){if(sp==null)sp=c.applicationContext.getSharedPreferences("AppState",Context.MODE_PRIVATE)}
 /** buang url yang di-cache untuk satu video (dipakai saat mengulang pemutaran yang gagal; url lama mungkin sudah kedaluwarsa) */
 fun forget(video:String){urls.remove(qKey(video,true));urls.remove(qKey(video,false))}
 /** url audio langsung (blocking; dipanggil dari thread loader ExoPlayer). Url YouTube kedaluwarsa, jadi di-cache 4 jam. */
 fun streamUrl(video:String):String{
  // true = ambil stream dengan bitrate terbesar (format apa pun, biasanya Opus); false = utamakan M4A/AAC
  val max=sp?.getBoolean("yt_max_quality",false)==true
  val key=qKey(video,max)
  urls[key]?.let{if(it.second>System.currentTimeMillis())return it.first}
  ready
  val info=StreamInfo.getInfo(svc,"https://www.youtube.com/watch?v=$video")
  val a=info.audioStreams.filter{it.isUrl}
  val best=(if(max)a.maxByOrNull{it.averageBitrate}
   else a.filter{it.format==MediaFormat.M4A}.maxByOrNull{it.averageBitrate}?:a.maxByOrNull{it.averageBitrate})
   ?:throw IOException("Tidak ada stream audio")
  metas[key]=StreamMeta(best.format?.name?:"?",best.format?.mimeType,best.averageBitrate,best.itagItem?.codec,best.itagItem?.sampleRate?:0,max)
  return best.content.also{urls[key]=it to System.currentTimeMillis()+4*3600_000L}
 }
}

/** Mengubah yt://<id video> menjadi url audio asli saat ExoPlayer membuka sumber. */
@UnstableApi
object YtResolver:ResolvingDataSource.Resolver{
 override fun resolveDataSpec(dataSpec:DataSpec):DataSpec{
  val u=dataSpec.uri
  if(u.scheme!="yt")return dataSpec
  val id=u.schemeSpecificPart.removePrefix("//")
  val url=try{Yt.streamUrl(id)}catch(e:Exception){throw IOException("YouTube: ${e.message}",e)}
  return dataSpec.withUri(Uri.parse(url))
 }
}