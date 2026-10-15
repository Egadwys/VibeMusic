package com.ega.project.vibemusic
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Menghitung spektrum (FFT 512) dari PCM yang sedang diputar. Dibaca UI lewat [levels] (0..1 per band). */
object Spectrum{
 const val N=512
 const val B=28
 @Volatile var levels=FloatArray(B)
 @Volatile private var lastFeed=0L
 /** true bila tap audio baru saja menerima data (UI memakai spektrum asli; selain itu animasi dekoratif) */
 fun live()=System.nanoTime()-lastFeed<400_000_000L
 private val re=FloatArray(N)
 private val im=FloatArray(N)
 private var n=0

 fun feed(input:ByteBuffer,ch:Int){
  try{feedInner(input,ch)}catch(e:Throwable){android.util.Log.e("VibePlayer","Spectrum.feed gagal",e)}
 }
 private fun feedInner(input:ByteBuffer,ch:Int){
  lastFeed=System.nanoTime()
  val sb=input.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
  while(sb.remaining()>=ch){
   var s=0f
   for(k in 0 until ch)s+=sb.get()
   re[n++]=s/ch/32768f
   if(n==N){compute();n=0}
  }
 }

 private fun compute(){
  for(i in 0 until N){re[i]*=(0.5f-0.5f*cos(2f*PI.toFloat()*i/N));im[i]=0f}
  var j=0
  for(i in 1 until N){
   var bit=N shr 1
   while((j and bit)!=0){j=j xor bit;bit=bit shr 1}
   j=j xor bit
   if(i<j){val t=re[i];re[i]=re[j];re[j]=t}
  }
  var len=2
  while(len<=N){
   val ang=-2f*PI.toFloat()/len;val h=len/2
   var i=0
   while(i<N){
    for(k in 0 until h){
     val wr=cos(ang*k);val wi=sin(ang*k)
     val a=i+k;val b=a+h
     val tr=re[b]*wr-im[b]*wi;val ti=re[b]*wi+im[b]*wr
     re[b]=re[a]-tr;im[b]=im[a]-ti;re[a]+=tr;im[a]+=ti
    }
    i+=len
   }
   len=len shl 1
  }
  val out=FloatArray(B)
  val top=200f
  for(b in 0 until B){
   val lo=top.pow(b.toFloat()/B).toInt().coerceAtLeast(1)
   val hi=maxOf(top.pow((b+1f)/B).toInt(),lo+1)
   var m=0f
   for(k in lo until hi)m+=sqrt(re[k]*re[k]+im[k]*im[k])
   m/=(hi-lo)
   val db=20f*log10(m/(N/4f)+1e-6f)
   out[b]=((db+72f+b*0.6f)/52f).coerceIn(0f,1f)
  }
  levels=out
 }
}

/**
 * Pembungkus AudioSink: audio diteruskan apa adanya ke DefaultAudioSink, lalu byte PCM 16-bit yang
 * baru dikonsumsi sink disalin (baca-saja) ke [Spectrum]. Tidak ada buffer output tambahan, jadi
 * tidak ada risiko "source buffer is this buffer" seperti pada AudioProcessor yang menyalin buffer.
 */
@OptIn(UnstableApi::class)
class SpectrumSink(sink:AudioSink):ForwardingAudioSink(sink){
 private var ch=2
 private var pcm16=false
 override fun configure(inputFormat:Format,specifiedBufferSize:Int,outputChannels:IntArray?){
  pcm16=inputFormat.pcmEncoding==C.ENCODING_PCM_16BIT
  ch=inputFormat.channelCount.coerceAtLeast(1)
  super.configure(inputFormat,specifiedBufferSize,outputChannels)
 }
 override fun handleBuffer(buffer:ByteBuffer,presentationTimeUs:Long,encodedAccessUnitCount:Int):Boolean{
  val start=buffer.position()
  val ok=super.handleBuffer(buffer,presentationTimeUs,encodedAccessUnitCount)
  if(pcm16){
   val end=buffer.position()
   if(end>start){val d=buffer.duplicate();d.limit(end);d.position(start);Spectrum.feed(d,ch)}
  }
  return ok
 }
}
