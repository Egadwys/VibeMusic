package com.ega.project.vibemusic
import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.media3.common.*
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.*

/**
 * Error pemutaran terakhir; dibaca UI untuk ditampilkan di dialog (service dan UI satu proses).
 * [net] = true bila error berasal dari lagu YouTube yang gagal dimuat setelah diulang dan dilewati (dianggap masalah jaringan).
 */
object PlayerError{
 var text by mutableStateOf<String?>(null)
 var net by mutableStateOf(false)
 fun clear(){text=null;net=false}
}

@OptIn(UnstableApi::class)
class PlaybackService:MediaSessionService(){
 private var session:MediaSession?=null
 private var exo:ExoPlayer?=null
 private val h=Handler(Looper.getMainLooper())
 private val sleepStop=Runnable{session?.player?.pause();clearSleep()}
 private fun prefs()=getSharedPreferences("s",0)

 // ---- penanganan error lagu YouTube: ulangi sekali -> lompat ke lagu berikutnya -> bila masih gagal tampilkan popup
 private var errId:String?=null   // lagu yang sedang bermasalah
 private var errStage=0           // 0 = normal, 1 = sedang diulang, 2 = sudah lompat ke lagu berikutnya
 private fun resetErr(){errId=null;errStage=0}
 private fun popup(txt:String,net:Boolean){h.post{PlayerError.net=net;PlayerError.text=txt}}
 /** indeks lagu berikutnya di antrean (mengikuti shuffle/repeat); UNSET bila tidak ada lagu lain untuk dilompati */
 private fun nextIndex():Int{
  val p=exo?:return C.INDEX_UNSET
  val cur=p.currentMediaItemIndex
  val n=p.nextMediaItemIndex
  if(n!=C.INDEX_UNSET&&n!=cur)return n
  // repeat satu lagu: ExoPlayer akan mengulang lagu yang sama, jadi geser manual
  return if(p.repeatMode==Player.REPEAT_MODE_ONE&&cur+1<p.mediaItemCount)cur+1 else C.INDEX_UNSET
 }
 private fun onYtError(p:ExoPlayer,item:MediaItem,txt:String){
  val id=item.mediaId
  if(id!=errId){errId=id;errStage=0}   // lagu lain (mis. pengguna pindah sendiri): mulai dari awal
  when(errStage){
   0->{
    // gagal pertama: ulangi sekali. Url lama dibuang dulu karena url YouTube bisa kedaluwarsa
    errStage=1
    item.localConfiguration?.uri?.schemeSpecificPart?.removePrefix("//")?.let{Yt.forget(it)}
    h.postDelayed({if(p.currentMediaItem?.mediaId==id&&p.playbackState==Player.STATE_IDLE)p.prepare()},700L)
   }
   1->{
    // ulangan gagal: lompat ke lagu berikutnya
    val n=nextIndex()
    if(n==C.INDEX_UNSET){resetErr();popup(txt,true)}
    else{
     errStage=2;errId=p.getMediaItemAt(n).mediaId
     p.seekTo(n,0L);p.prepare()
    }
   }
   else->{resetErr();popup(txt,true)}   // lagu berikutnya juga gagal: kemungkinan besar masalah jaringan
  }
 }

 private fun clearSleep(){
  h.removeCallbacks(sleepStop)
  exo?.setPauseAtEndOfMediaItems(false)
  prefs().edit().remove("sleep_end").apply()
 }
 /** min>0: berhenti setelah min menit; min<0: berhenti di akhir lagu; 0: batalkan */
 private fun setSleep(min:Int){
  clearSleep()
  when{
   min>0->{h.postDelayed(sleepStop,min*60_000L);prefs().edit().putLong("sleep_end",System.currentTimeMillis()+min*60_000L).apply()}
   min<0->{exo?.setPauseAtEndOfMediaItems(true);prefs().edit().putLong("sleep_end",-1L).apply()}
   else->Unit
  }
 }

 override fun onCreate(){
  super.onCreate()
  prefs().edit().remove("sleep_end").apply()
  Yt.init(this)
  // Renderer audio dengan SpectrumSink: audio tidak diubah, hanya dibaca untuk visualizer
  val renderers=RenderersFactory{handler,_,audioListener,_,_->
   arrayOf<Renderer>(MediaCodecAudioRenderer(this@PlaybackService,MediaCodecSelector.DEFAULT,handler,audioListener,
    SpectrumSink(DefaultAudioSink.Builder(this@PlaybackService).build())))
  }
  // DIAGNOSA: ubah ke false untuk mematikan SpectrumSink (visualizer jadi animasi dekoratif)
  val useTap=true
  // sumber media: file lokal tetap lewat DefaultDataSource; alamat yt://<id> diubah jadi url audio YouTube saat dibuka
  val http=DefaultHttpDataSource.Factory().setUserAgent(Yt.UA).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
  val sources=DefaultMediaSourceFactory(ResolvingDataSource.Factory(DefaultDataSource.Factory(this,http),YtResolver))
  val p=ExoPlayer.Builder(this,if(useTap)renderers else androidx.media3.exoplayer.DefaultRenderersFactory(this)).setMediaSourceFactory(sources).setAudioAttributes(AudioAttributes.DEFAULT,true).setHandleAudioBecomingNoisy(true).build()
  exo=p
  p.addListener(object:Player.Listener{
   override fun onEvents(pl:Player,e:Player.Events){
    PlayerWidget.push(this@PlaybackService,pl.mediaMetadata.title?.toString(),pl.mediaMetadata.artist?.toString(),pl.isPlaying)
   }
   override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){
    // sleep timer akhir lagu: setelah player berhenti, matikan penandanya
    if(reason==Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)clearSleep()
   }
   override fun onPlayerError(error:PlaybackException){
    Log.e("VibePlayer","Playback error: ${error.errorCodeName}",error)
    val txt="${error.errorCodeName} (${error.errorCode})\n${error.message}\n\n${Log.getStackTraceString(error)}"
    val item=p.currentMediaItem
    if(item!=null&&item.localConfiguration?.uri?.scheme=="yt")onYtError(p,item,txt)
    else{resetErr();popup(txt,false)}   // lagu lokal: tetap popup error seperti biasa
   }
   override fun onPlaybackStateChanged(state:Int){
    if(state==Player.STATE_READY)resetErr()   // berhasil dimuat: hitungan gagal dimulai dari nol lagi
   }
   override fun onAudioSessionIdChanged(audioSessionId:Int){Eq.attach(this@PlaybackService,audioSessionId)}
  })
  Eq.attach(this,p.audioSessionId)
  session=MediaSession.Builder(this,FadingPlayer(p)).setSessionActivity(
   PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)).build()
 }
 override fun onGetSession(i:MediaSession.ControllerInfo)=session
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  session?.player?.let{when(intent?.action){
   "pp"->if(it.isPlaying)it.pause() else it.play()
   "next"->it.seekToNext(); "prev"->it.seekToPrevious()
   "sleep"->setSleep(intent?.getIntExtra("min",0)?:0)
   else->Unit}}
  return super.onStartCommand(intent,flags,startId)
 }
 override fun onDestroy(){
  h.removeCallbacks(sleepStop);prefs().edit().remove("sleep_end").apply()
  Eq.release()
  session?.run{player.release();release()};session=null;exo=null;super.onDestroy()
 }
}

class PlayerWidget:AppWidgetProvider(){
 override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray)=push(c,null,null,false)
 companion object{
  fun push(c:Context,t:String?,a:String?,playing:Boolean){
   val v=RemoteViews(c.packageName,R.layout.widget_player)
   v.setTextViewText(R.id.wt,t?:"VibeMusic");v.setTextViewText(R.id.wa,a?:"Belum memutar")
   v.setImageViewResource(R.id.wpp,if(playing)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
   fun pi(act:String)=PendingIntent.getService(c,act.hashCode(),Intent(c,PlaybackService::class.java).setAction(act),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   v.setOnClickPendingIntent(R.id.wp,pi("prev"));v.setOnClickPendingIntent(R.id.wpp,pi("pp"));v.setOnClickPendingIntent(R.id.wn,pi("next"))
   v.setOnClickPendingIntent(R.id.wroot,PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
   AppWidgetManager.getInstance(c).updateAppWidget(ComponentName(c,PlayerWidget::class.java),v)
  }
 }
}