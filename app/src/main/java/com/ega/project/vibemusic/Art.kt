package com.ega.project.vibemusic
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/*
 * COVER LAGU ONLINE
 * 1) Pihak ketiga: Deezer (API pencarian publik tanpa kunci, cover 1000x1000). Hasil dipakai hanya bila judul cocok
 *    dan (artis cocok atau durasi selisih <= 5 detik), supaya tidak salah cover.
 * 2) Jika tidak ada / jaringan gagal: thumbnail YouTube resolusi tertinggi (maxresdefault, 1280x720, dipotong persegi di tengah),
 *    lalu url thumbnail asli (versi googleusercontent diminta ukuran 1000px).
 */
object Art{
 private val found=ConcurrentHashMap<Long,String>()   // id lagu -> url cover Deezer ("" = memang tidak ada)
 private val imgRe=Regex("""i\.ytimg\.com/vi(?:_webp)?/([\w-]{11})/""")
 private val sizeRe=Regex("""=[wsh]\d+.*$""")
 private val nonWord=Regex("""[^\p{L}\p{N}]""")

 private fun e(x:String)=URLEncoder.encode(x,"UTF-8")
 private fun norm(t:String)=t.substringBefore("(").substringBefore("[").lowercase().replace(nonWord,"")
 private fun open(u:String):HttpURLConnection{
  val cn=URL(u).openConnection() as HttpURLConnection
  cn.connectTimeout=8000;cn.readTimeout=8000;cn.setRequestProperty("User-Agent",Yt.UA)
  return cn
 }
 private fun text(u:String):String=open(u).inputStream.bufferedReader().use{it.readText()}
 private fun bitmap(u:String):Bitmap=open(u).inputStream.use{BitmapFactory.decodeStream(it)}?:throw IOException("decode")

 /** potong persegi di tengah, lalu kecilkan bila lebih besar dari [px] */
 private fun fit(raw:Bitmap,px:Int):Bitmap{
  val m=minOf(raw.width,raw.height)
  val sq=if(raw.width==raw.height)raw else Bitmap.createBitmap(raw,(raw.width-m)/2,(raw.height-m)/2,m,m)
  return if(m>px)Bitmap.createScaledBitmap(sq,px,px,true) else sq
 }

 /** cover yang tertanam di metadata berkas audio (ID3/MP4 dll). null = tidak ada. Didecode berskala agar hemat memori. */
 fun embedded(c:Context,s:Song,px:Int):Bitmap?{
  val r=android.media.MediaMetadataRetriever()
  return try{
   r.setDataSource(c,s.uri)
   val b=r.embeddedPicture
   if(b==null)null else{
    val o=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(b,0,b.size,o)
    var k=1
    while(o.outWidth/(k*2)>=px&&o.outHeight/(k*2)>=px)k*=2
    BitmapFactory.decodeByteArray(b,0,b.size,BitmapFactory.Options().apply{inSampleSize=k})?.let{fit(it,px)}
   }
  }catch(_:Exception){null}finally{runCatching{r.release()}}
 }

 /** url cover Deezer yang cocok dengan lagu, atau null. Melempar IOException bila jaringan/server bermasalah (jangan di-cache). */
 private fun deezer(s:Song):String?{
  val title=Yt.cleanTitle(s.title,s.artist)
  val nt=norm(title);val na=norm(s.artist);val sec=s.dur/1000
  if(nt.isEmpty())return null
  for(q in listOf("artist:\"${s.artist}\" track:\"$title\"","${s.artist} $title")){
   val j=JSONObject(text("https://api.deezer.com/search?limit=5&q=${e(q)}"))
   if(j.has("error"))throw IOException("deezer: ${j.optJSONObject("error")?.optString("message")}")
   val a=j.optJSONArray("data")?:continue
   for(i in 0 until a.length()){
    val o=a.getJSONObject(i)
    val ot=norm(o.optString("title_short").ifBlank{o.optString("title")})
    val oa=norm(o.optJSONObject("artist")?.optString("name").orEmpty())
    val titleOk=ot.isNotEmpty()&&(ot==nt||ot.contains(nt)||nt.contains(ot))
    val artistOk=oa.isNotEmpty()&&na.isNotEmpty()&&(oa.contains(na)||na.contains(oa))
    val durOk=kotlin.math.abs(o.optLong("duration")-sec)<=5
    if(titleOk&&(artistOk||durOk)){
     val al=o.optJSONObject("album")?:continue
     val u=al.optString("cover_xl").ifBlank{al.optString("cover_big")}
     if(u.isNotBlank())return u
    }
   }
  }
  return null
 }

 private fun hi(u:String)=if("googleusercontent.com" in u||"ggpht.com" in u)sizeRe.replace(u,"=w1000-h1000-l90-rj") else u

 /** thumbnail YouTube resolusi tertinggi yang tersedia */
 private fun ytHd(s:Song):Bitmap?{
  val vid=s.web?:s.thumb?.let{imgRe.find(it)?.groupValues?.get(1)}
  val urls=listOfNotNull(vid?.let{"https://i.ytimg.com/vi/$it/maxresdefault.jpg"},s.thumb?.let{hi(it)})
  for(u in urls){try{return bitmap(u)}catch(_:Exception){}}   // maxres tidak ada di semua video (404) -> coba berikutnya
  return null
 }

 /** dipanggil dari Covers.get (thread IO). null = tidak ada cover. */
 fun load(c:Context,s:Song,px:Int):Bitmap?{
  // playlist (tanpa id video) tidak dicari di Deezer; hanya thumbnail-nya dinaikkan resolusinya
  if(s.web!=null){
   val sp=c.getSharedPreferences("art",0)
   val key=s.id.toString()
   var u:String?=found[s.id]?:sp.getString(key,null)?.also{found[s.id]=it}
   if(u==null){
    u=try{deezer(s)?:""}catch(_:Exception){null}
    if(u!=null){found[s.id]=u;sp.edit().putString(key,u).apply()}
   }
   if(!u.isNullOrEmpty())try{return fit(bitmap(u),px)}catch(_:Exception){}
  }
  return ytHd(s)?.let{fit(it,px)}
 }
}