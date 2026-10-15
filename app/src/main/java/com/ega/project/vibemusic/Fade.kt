package com.ega.project.vibemusic
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Efek fade pada volume: play = fade in, pause = fade out (baru benar-benar berhenti setelah fade selesai),
 * next/prev/lompat lagu = fade out singkat -> pindah lagu -> fade in.
 * Dipasang di service sehingga berlaku untuk semua sumber: tombol di app, notifikasi, widget, headset, sleep timer.
 * Ubah angka durasi (ms) di bawah untuk lebih cepat/lambat.
 */
@OptIn(UnstableApi::class)
class FadingPlayer(private val exo:ExoPlayer):ForwardingPlayer(exo){
 companion object{
  const val PLAY_IN_MS=350L     // fade in saat play
  const val PAUSE_OUT_MS=250L   // fade out saat pause
  const val SKIP_OUT_MS=120L    // fade out sebelum pindah lagu
  const val SKIP_IN_MS=300L     // fade in setelah pindah lagu
 }
 private val h=Handler(Looper.getMainLooper())
 private var anim:Runnable?=null
 private var pending:(()->Unit)?=null   // aksi pindah lagu yang menunggu fade out selesai

 private fun stopAnim(){anim?.let{h.removeCallbacks(it)};anim=null}
 /** jalankan langsung aksi yang masih menunggu (mis. next ditekan cepat berulang) */
 private fun flush(){val p=pending;pending=null;p?.invoke()}

 private fun ramp(to:Float,ms:Long,end:()->Unit={}){
  stopAnim()
  val from=exo.volume;val t0=SystemClock.uptimeMillis()
  val r=object:Runnable{
   override fun run(){
    if(anim!==this)return
    val k=((SystemClock.uptimeMillis()-t0)/ms.toFloat()).coerceIn(0f,1f)
    exo.volume=from+(to-from)*k
    if(k<1f)h.postDelayed(this,16L) else{anim=null;end()}
   }
  }
  anim=r;r.run()
 }

 override fun play(){
  flush()
  if(!exo.playWhenReady)exo.volume=0f
  exo.play()
  ramp(1f,PLAY_IN_MS)
 }

 override fun pause(){
  flush()
  if(!exo.playWhenReady){exo.pause();return}
  ramp(0f,PAUSE_OUT_MS){exo.pause();exo.volume=1f}
 }

 private fun skip(act:()->Unit){
  flush()
  if(!exo.playWhenReady){act();return}   // sedang pause: tidak perlu fade
  pending=act
  ramp(0f,SKIP_OUT_MS){flush();ramp(1f,SKIP_IN_MS)}
 }

 override fun seekToNext(){skip{exo.seekToNext()}}
 override fun seekToPrevious(){skip{exo.seekToPrevious()}}
 override fun seekToNextMediaItem(){skip{exo.seekToNextMediaItem()}}
 override fun seekToPreviousMediaItem(){skip{exo.seekToPreviousMediaItem()}}
 override fun seekTo(mediaItemIndex:Int,positionMs:Long){
  if(mediaItemIndex!=exo.currentMediaItemIndex)skip{exo.seekTo(mediaItemIndex,positionMs)} else exo.seekTo(mediaItemIndex,positionMs)
 }
}
