package com.ega.project.vibemusic
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [chips] = ringkasan kualitas (format, bitrate, frekuensi); [rows] = pasangan label -> nilai */
class SongDetail(val chips:List<String>,val rows:List<Pair<String,String>>)

/** Mengumpulkan info lagu: lagu lokal dari metadata berkas + MediaStore, lagu YouTube dari stream yang sedang dipakai. Blocking (panggil dari IO). */
object Info{
 private fun khz(hz:Int)=if(hz%1000==0)"${hz/1000} kHz" else "%.1f kHz".format(hz/1000.0)
 private fun fmtName(n:String)=when(n.uppercase()){"M4A"->"AAC (M4A)";"WEBMA_OPUS"->"Opus (WebM)";"WEBMA"->"Vorbis (WebM)";else->n}

 fun load(c:Context,s:Song):SongDetail=if(s.web!=null)online(s) else local(c,s)

 private fun online(s:Song):SongDetail{
  val chips=ArrayList<String>();val rows=ArrayList<Pair<String,String>>()
  rows+="Sumber" to "YouTube"
  val m=Yt.streamMeta(s.web!!)
  if(m!=null){
   chips+=fmtName(m.format)
   if(m.kbps>0)chips+="${m.kbps} kbps"
   if(m.hz>0)chips+=khz(m.hz)
   rows+="Kualitas" to if(m.max)"Tertinggi" else "Standar"
   m.codec?.takeIf{it.isNotBlank()}?.let{rows+="Codec" to it}
   m.mime?.takeIf{it.isNotBlank()}?.let{rows+="Tipe media" to it}
  }else rows+="Stream" to "Belum dimuat"
  rows+="Durasi" to fmt(s.dur)
  rows+="ID video" to s.web!!
  return SongDetail(chips,rows)
 }

 private fun local(c:Context,s:Song):SongDetail{
  var mime:String?=null;var kb:Long?=null;var hz=0;var bits=0;var year:String?=null
  val r=MediaMetadataRetriever()
  try{
   r.setDataSource(c,s.uri)
   fun k(key:Int)=r.extractMetadata(key)?.takeIf{it.isNotBlank()}
   mime=k(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
   kb=k(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.div(1000)
   year=k(MediaMetadataRetriever.METADATA_KEY_YEAR)
   if(Build.VERSION.SDK_INT>=31){
    hz=k(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?:0
    bits=k(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?:0
   }
  }catch(_:Exception){}finally{runCatching{r.release()}}
  var size=0L;var name:String?=null;var path:String?=null
  runCatching{
   c.contentResolver.query(s.uri,arrayOf(MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.RELATIVE_PATH),null,null,null)?.use{cu->
    if(cu.moveToFirst()){size=cu.getLong(0);name=cu.getString(1);path=cu.getString(2)}
   }
  }
  val ext=name?.substringAfterLast('.',"")?.takeIf{it.isNotBlank()&&it.length<=5}?.uppercase()?:mime?.substringAfter('/')?.uppercase()
  val rate=kb?:if(size>0&&s.dur>0)size*8/s.dur else null   // bit per ms = kbps
  val chips=ArrayList<String>()
  ext?.let{chips+=it}
  rate?.takeIf{it>0}?.let{chips+="$it kbps"}
  if(hz>0)chips+=khz(hz)
  if(bits>0)chips+="$bits-bit"
  val rows=ArrayList<Pair<String,String>>()
  if(s.album!=UB)rows+="Album" to s.album
  if(s.genre!=UG)rows+="Genre" to s.genre
  year?.let{rows+="Tahun" to it}
  rows+="Durasi" to fmt(s.dur)
  if(size>0)rows+="Ukuran" to "%.1f MB".format(size/1048576.0)
  name?.let{rows+="Berkas" to it}
  path?.takeIf{it.isNotBlank()}?.let{rows+="Lokasi" to it}
  return SongDetail(chips,rows)
 }
}

/** Kartu info lagu (muncul saat cover di pemutar ditekan lama). Ketuk di luar kartu untuk menutup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SongInfoCard(s:Song,onDismiss:()->Unit){
 val c=LocalContext.current
 val d by produceState<SongDetail?>(null,s.id){value=withContext(Dispatchers.IO){runCatching{Info.load(c,s)}.getOrNull()}}
 val cs=MaterialTheme.colorScheme
 Dialog(onDismissRequest=onDismiss){
  Surface(shape=RoundedCornerShape(28.dp),color=cs.surfaceContainerHigh,tonalElevation=6.dp){
   Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){
     Cover(s,Modifier.size(72.dp),shape=RoundedCornerShape(18.dp),px=200)
     Spacer(Modifier.width(14.dp))
     Column(Modifier.weight(1f)){
      Text(s.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
      Text(s.artist,style=MaterialTheme.typography.bodyMedium,color=cs.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
     }
    }
    Spacer(Modifier.height(16.dp))
    val x=d
    if(x==null)Box(Modifier.fillMaxWidth().height(80.dp),Alignment.Center){CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=3.dp)}
    else{
     if(x.chips.isNotEmpty()){
      FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       x.chips.forEach{t->
        Surface(shape=RoundedCornerShape(50),color=cs.primaryContainer){
         Text(t,Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold,color=cs.onPrimaryContainer)
        }
       }
      }
      Spacer(Modifier.height(8.dp))
     }
     x.rows.forEach{(k,v)->
      Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.Top){
       Text(k,Modifier.width(88.dp),style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)
       Text(v,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.End)
      }
     }
    }
   }
  }
 }
}