package com.ega.project.vibemusic
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
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

/** Memuat seluruh lagu dari MediaStore */
fun loadSongs(c:Context):List<Song>{
 val out=ArrayList<Song>()
 val meta=c.getSharedPreferences("meta",0)
 fun cl(s:String?,d:String)=if(s.isNullOrBlank()||s=="<unknown>")d else s
 c.contentResolver.query(M.EXTERNAL_CONTENT_URI,arrayOf(M._ID,M.TITLE,M.ARTIST,M.ALBUM,M.GENRE,M.DURATION,M.RELATIVE_PATH),
  "${M.IS_MUSIC}!=0",null,"${M.TITLE} COLLATE NOCASE")?.use{cu->
  while(cu.moveToNext()){
   val parts=(cu.getString(6)?:"").split("/").filter{it.isNotBlank()}
   val folder=if(parts.firstOrNull().equals("Music",true))(parts.getOrNull(1)?:"Music") else null
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

/** Memuat lagu dengan filter folder pilihan user melalui MediaStore */
fun loadSongsFromFolder(context: Context, folderUri: Uri): List<Song> {
    return loadSongs(context)
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
object Lyrics{
 private val cache=HashMap<Long,List<Line>>()
 private val re=Regex("""\[(\d+):(\d+)(?:\.(\d+))?](.*)""")
 suspend fun get(s:Song):List<Line> = cache[s.id]?:withContext(Dispatchers.IO){
  try{
   fun e(x:String)=URLEncoder.encode(x,"UTF-8")
   val u="https://lrclib.net/api/get?track_name=${e(s.title)}&artist_name=${e(s.artist)}&album_name=${e(s.album)}&duration=${s.dur/1000}"
   val c=URL(u).openConnection() as HttpURLConnection
   c.setRequestProperty("User-Agent","VibeMusic/1.0");c.connectTimeout=8000;c.readTimeout=8000
   val t=JSONObject(c.inputStream.bufferedReader().readText()).optString("syncedLyrics")
   t.lines().mapNotNull{l->re.matchEntire(l.trim())?.let{m->
    val(mm,ss,f,tx)=m.destructured
    Line(mm.toLong()*60000+ss.toLong()*1000+(f+"000").take(3).toLong(),tx.trim())}}
  }catch(e:Exception){emptyList()}
 }.also{cache[s.id]=it}
}
