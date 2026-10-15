package com.ega.project.vibemusic
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer

/** Sleep timer. Alarm dijalankan di [PlaybackService] supaya tetap jalan walau aplikasi ditutup. */
object Sleep{
 /** 0 = tidak aktif, -1 = berhenti di akhir lagu, >0 = waktu berhenti (epoch ms) */
 fun end(c:Context)=c.getSharedPreferences("s",0).getLong("sleep_end",0L)
 /** min>0: berhenti setelah min menit; min<0: berhenti di akhir lagu; 0: batalkan */
 fun set(c:Context,min:Int){
  val e=when{min>0->System.currentTimeMillis()+min*60_000L;min<0->-1L;else->0L}
  val ed=c.getSharedPreferences("s",0).edit()
  if(e==0L)ed.remove("sleep_end") else ed.putLong("sleep_end",e)
  ed.apply()
  c.startService(Intent(c,PlaybackService::class.java).setAction("sleep").putExtra("min",min))
 }
}

/** Equalizer sistem (android.media.audiofx) yang menempel ke sesi audio ExoPlayer. */
object Eq{
 private var fx:Equalizer?=null
 private var lv:IntArray?=null
 private var enabled=false

 val bands:Int get()=fx?.numberOfBands?.toInt()?:0
 val range:ShortArray? get()=fx?.bandLevelRange
 fun label(b:Int):String{
  val hz=(fx?.getCenterFreq(b.toShort())?:0)/1000
  return if(hz>=1000)"%.1f kHz".format(hz/1000f) else "$hz Hz"
 }

 private fun load(c:Context){
  if(lv!=null)return
  val sp=c.getSharedPreferences("eq",0)
  enabled=sp.getBoolean("on",false)
  lv=sp.getString("lv","")!!.split(",").mapNotNull{it.toIntOrNull()}.toIntArray()
 }
 fun isOn(c:Context):Boolean{load(c);return enabled}
 fun level(c:Context,b:Int):Int{load(c);return lv!!.getOrElse(b){0}}
 fun setOn(c:Context,v:Boolean){load(c);enabled=v;save(c);apply()}
 fun setLevel(c:Context,b:Int,v:Int){
  load(c)
  if(lv!!.size<=b)lv=lv!!.copyOf(b+1)
  lv!![b]=v
  apply()
 }
 fun save(c:Context){
  load(c)
  c.getSharedPreferences("eq",0).edit().putBoolean("on",enabled).putString("lv",lv!!.joinToString(",")).apply()
 }

 fun attach(c:Context,session:Int){
  release()
  if(session<=0)return
  load(c)
  fx=runCatching{Equalizer(0,session)}.getOrNull()
  apply()
 }
 fun release(){runCatching{fx?.release()};fx=null}

 private fun apply(){
  val e=fx?:return
  runCatching{
   val r=e.bandLevelRange
   e.setEnabled(enabled)
   for(b in 0 until e.numberOfBands.toInt()){
    val v=(lv?.getOrNull(b)?:0).coerceIn(r[0].toInt(),r[1].toInt())
    e.setBandLevel(b.toShort(),v.toShort())
   }
  }
 }
}
