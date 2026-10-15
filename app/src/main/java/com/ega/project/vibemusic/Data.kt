package com.ega.project.vibemusic
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore.Audio.Media as M
import android.util.Size
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.*
import java.net.*

const val UA="Artis tidak dikenal";const val UB="Album tidak dikenal";const val UG="Genre tidak dikenal"

data class Song(val id:Long,val title:String,val artist:String,val album:String,val genre:String,val dur:Long,val folder:String?){
 val uri get()=ContentUris.withAppendedId(M.EXTERNAL_CONTENT_URI,id)
}
fun Song.item()=MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
 .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()).build()

/**
 * Folder sumber musik pilihan pengguna. Disimpan sebagai id dokumen SAF (mis. "primary:Music/Rock").
 * Kosong = semua folder dipindai. Yang dipakai untuk menyaring hanya bagian path-nya (relatif terhadap akar penyimpanan),
 * dicocokkan ke kolom RELATIVE_PATH MediaStore, jadi tidak perlu izin tambahan selain akses musik.
 */
object Folders{
 private const val K="music_folders"
 private const val P="folders_prompted"
 private fun sp(c:Context)=c.getSharedPreferences("s",0)
 fun get(c:Context):List<String> = (sp(c).getStringSet(K,emptySet())?:emptySet()).toList().sorted()
 private fun put(c:Context,l:Collection<String>){sp(c).edit().putStringSet(K,l.toSet()).apply()}
 fun add(c:Context,id:String){put(c,get(c)+id)}
 fun remove(c:Context,id:String){put(c,get(c)-id)}
 fun clear(c:Context){sp(c).edit().remove(K).apply()}
 /** sudah pernah ditanya saat pertama kali membuka aplikasi? */
 fun prompted(c:Context)=sp(c).getBoolean(P,false)
 fun markPrompted(c:Context){sp(c).edit().putBoolean(P,true).apply()}
 fun fromTree(uri:Uri):String?=runCatching{DocumentsContract.getTreeDocumentId(uri)}.getOrNull()
 /** path relatif tanpa awal/akhir "/" ("" = akar penyimpanan) */
 fun path(id:String)=id.substringAfter(':',"").trim('/')
 fun label(id:String):String{
  val p=path(id)
  val vol=id.substringBefore(':')
  return (if(p.isEmpty())"Seluruh penyimpanan" else p)+(if(vol!="primary")" (kartu SD)" else "")
 }
}

/** Memuat lagu dari MediaStore; bila pengguna memilih folder, hanya lagu di dalam folder tersebut (termasuk subfolder) */
fun loadSongs(c:Context):List<Song>{
 val out=ArrayList<Song>()
 val meta=c.getSharedPreferences("meta",0)
 val sel=Folders.get(c).map{Folders.path(it).lowercase()}.filter{it.isNotEmpty()}.distinct()
 fun cl(s:String?,d:String)=if(s.isNullOrBlank()||s=="<unknown>")d else s
 c.contentResolver.query(M.EXTERNAL_CONTENT_URI,arrayOf(M._ID,M.TITLE,M.ARTIST,M.ALBUM,M.GENRE,M.DURATION,M.RELATIVE_PATH),
  "${M.IS_MUSIC}!=0",null,"${M.TITLE} COLLATE NOCASE")?.use{cu->
  while(cu.moveToNext()){
   val parts=(cu.getString(6)?:"").split("/").filter{it.isNotBlank()}
   // folder terpilih yang memuat lagu ini (null bila tidak ada pilihan = semua folder)
   var selDepth=0
   if(sel.isNotEmpty()){
    val low=parts.joinToString("/").lowercase()
    val m=sel.firstOrNull{low==it||low.startsWith("$it/")}?:continue
    selDepth=m.count{ch->ch=='/'}+1
   }
   // grup di tab Home: subfolder di dalam Music; untuk folder pilihan di luar Music, memakai nama folder pilihannya
   val folder=if(parts.firstOrNull().equals("Music",true))(parts.getOrNull(1)?:"Music") else if(selDepth>0)parts.take(selDepth).lastOrNull() else null
   var s=Song(cu.getLong(0),cl(cu.getString(1),"Tanpa judul"),cl(cu.getString(2),UA),cl(cu.getString(3),UB),cl(cu.getString(4),UG),cu.getLong(5),folder)
   meta.getString("${s.id}",null)?.split("\t")?.takeIf{it.size==3}?.let{v->
    s=s.copy(artist=if(s.artist==UA&&v[0].isNotBlank())v[0] else s.artist,
     album=if(s.album==UB&&v[1].isNotBlank())v[1] else s.album,
     genre=if(s.genre==UG&&v[2].isNotBlank())v[2] else s.genre)}
   out+=s
  }
 }
 return out
}

object Meta{
 fun tried(c:Context,id:Long)=c.getSharedPreferences("meta",0).contains("$id")
 suspend fun fetch(c:Context,s:Song):Song?=withContext(Dispatchers.IO){
  try{
   val q=URLEncoder.encode(s.title+(if(s.artist!=UA)" "+s.artist else ""),"UTF-8")
   val cn=URL("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=$q").openConnection() as HttpURLConnection
   cn.connectTimeout=8000;cn.readTimeout=8000
   val r=JSONObject(cn.inputStream.bufferedReader().readText()).getJSONArray("results")
   val ed=c.getSharedPreferences("meta",0).edit()
   if(r.length()==0){ed.putString("${s.id}","\t\t").apply();null}
   else{
    val o=r.getJSONObject(0)
    val n=s.copy(artist=if(s.artist==UA)o.optString("artistName").ifBlank{s.artist} else s.artist,
     album=if(s.album==UB)o.optString("collectionName").ifBlank{s.album} else s.album,
     genre=if(s.genre==UG)o.optString("primaryGenreName").ifBlank{s.genre} else s.genre)
    val a=if(n.artist==UA)"" else n.artist;val b=if(n.album==UB)"" else n.album;val g=if(n.genre==UG)"" else n.genre
    ed.putString("${s.id}","$a\t$b\t$g").apply();n
   }
  }catch(e:Exception){null}
 }
}

object Covers{
 private class Lru(max:Int):android.util.LruCache<Long,Bitmap>(max){override fun sizeOf(key:Long,value:Bitmap):Int=value.byteCount}
 private val small=Lru(24*1024*1024)
 private val big=Lru(16*1024*1024)
 private val none=java.util.Collections.synchronizedSet(HashSet<Long>())
 private val sem=Semaphore(3)
 fun peek(s:Song):Bitmap?=small.get(s.id)
 suspend fun get(c:Context,s:Song,px:Int):Bitmap?{
  val cache=if(px>512)big else small
  cache.get(s.id)?.let{return it}
  if(s.id in none)return null
  return sem.withPermit{withContext(Dispatchers.IO){
   try{c.contentResolver.loadThumbnail(s.uri,Size(px,px),null).also{cache.put(s.id,it)}}
   catch(e:Exception){none.add(s.id);null}
  }}
 }
}

class Playlists(c:Context){
 private val sp=c.getSharedPreferences("pl",0)
 val map=mutableStateMapOf<String,List<Long>>()
 init{ val j=JSONObject(sp.getString("d","{}")!!); j.keys().forEach{k->val a=j.getJSONArray(k);map[k]=List(a.length()){a.getLong(it)}} }
 private fun save()=sp.edit().putString("d",JSONObject(map.mapValues{JSONArray(it.value)}).toString()).apply()
 fun create(n:String){ if(n.isNotBlank()&&n !in map){map[n]=emptyList();save()} }
 fun delete(n:String){map.remove(n);save()}
 fun add(n:String,id:Long){ val l=map[n].orEmpty(); if(id !in l){map[n]=l+id;save()} }
 fun remove(n:String,id:Long){map[n]=map[n].orEmpty()-id;save()}
}

data class Line(val ms:Long,val text:String)
/** Lirik tersinkron dari LRCLIB. Hasilnya disimpan di filesDir/lyrics sehingga bisa dibuka offline. */
object Lyrics{
 private val mem=HashMap<Long,List<Line>>()
 private val re=Regex("""\[(\d+):(\d+)(?:\.(\d+))?](.*)""")
 // lagu tanpa lirik (404) dicek ulang setelah 7 hari
 private const val RETRY_MS=7L*24*3600*1000

 private fun parse(t:String):List<Line> = t.lines().mapNotNull{l->re.matchEntire(l.trim())?.let{m->
  val(mm,ss,f,tx)=m.destructured
  Line(mm.toLong()*60000+ss.toLong()*1000+(f+"000").take(3).toLong(),tx.trim())}}

 private fun file(c:Context,s:Song):java.io.File{
  val dir=java.io.File(c.filesDir,"lyrics");dir.mkdirs()
  return java.io.File(dir,"${s.id}.lrc")
 }

 suspend fun get(c:Context,s:Song):List<Line> = mem[s.id]?:withContext(Dispatchers.IO){
  val f=file(c,s)
  // 1) cache di perangkat: tidak butuh internet
  if(f.exists()){
   val txt=runCatching{f.readText()}.getOrDefault("")
   if(txt.isNotBlank()||System.currentTimeMillis()-f.lastModified()<RETRY_MS)
    return@withContext parse(txt).also{mem[s.id]=it}
  }
  // 2) ambil dari LRCLIB lalu simpan
  try{
   fun e(x:String)=URLEncoder.encode(x,"UTF-8")
   val u="https://lrclib.net/api/get?track_name=${e(s.title)}&artist_name=${e(s.artist)}&album_name=${e(s.album)}&duration=${s.dur/1000}"
   val cn=URL(u).openConnection() as HttpURLConnection
   cn.setRequestProperty("User-Agent","VibeMusic/1.0");cn.connectTimeout=8000;cn.readTimeout=8000
   when(cn.responseCode){
    200->{
     val t=JSONObject(cn.inputStream.bufferedReader().readText()).optString("syncedLyrics").let{if(it=="null")"" else it}
     runCatching{f.writeText(t)}
     parse(t).also{mem[s.id]=it}
    }
    404->{runCatching{f.writeText("")};emptyList<Line>().also{mem[s.id]=it}}
    else->emptyList() // galat server/jaringan: jangan disimpan, coba lagi nanti
   }
  }catch(e:Exception){emptyList()}
 }
}
