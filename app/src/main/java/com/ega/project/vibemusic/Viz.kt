package com.ega.project.vibemusic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Data bersama untuk semua visualizer batang/gelombang: band spektrum (0..1) yang sudah dihaluskan per frame,
 * waktu animasi, dan energi (turun halus ke 0 saat pause). [tick] berubah tiap frame; dibaca di Canvas agar digambar ulang.
 */
class VizFrame{
 val bands=FloatArray(Spectrum.B)
 var time=0f
 var energy=0f
 val tick=mutableLongStateOf(0L)
}

/** Animasi dekoratif bila tap audio belum aktif (mis. format tidak didukung). */
private fun decor(b:Int,t:Float):Float{
 val m=b/(Spectrum.B-1f)
 val v=0.5f+0.5f*sin(t*2.2f+m*7f)*sin(t*1.3f+m*3.1f)
 return ((0.25f+0.75f*v*v+0.1f*sin(t*5f+m*13f))*(1f-0.45f*m)).coerceIn(0.08f,1f)
}

/** Memperbarui [VizFrame] tiap frame selama musik diputar (atau energi belum habis) dan sheet pemutar terlihat. */
@Composable fun rememberViz(playing:Boolean,active:Boolean):VizFrame{
 val f=remember{VizFrame()}
 val en=animateFloatAsState(if(playing)1f else 0f,tween(450),label="vizEnergy")
 LaunchedEffect(playing,active){
  if(!active)return@LaunchedEffect
  var last=withFrameNanos{it}
  while(true){
   val now=withFrameNanos{it}
   val dt=((now-last)/1e9f).coerceIn(0f,0.1f)
   last=now
   f.time+=dt
   f.energy=en.value
   val live=Spectrum.live()
   val lv=Spectrum.levels
   // konstanta waktu (detik): naik cepat = responsif, turun pelan = mulus; tidak bergantung frame rate
   val aUp=1f-exp(-dt/0.030f)
   val aDn=1f-exp(-dt/0.170f)
   for(b in 0 until Spectrum.B){
    val tgt=if(live){
     // haluskan antar band tetangga agar tidak bergerigi
     val p=lv[max(b-1,0)];val n=lv[min(b+1,Spectrum.B-1)]
     0.25f*p+0.5f*lv[b]+0.25f*n
    }else decor(b,f.time)
    val cur=f.bands[b]
    f.bands[b]=cur+(tgt-cur)*(if(tgt>cur)aUp else aDn)
   }
   f.tick.longValue=now
   if(!playing&&en.value<0.01f)break
  }
 }
 return f
}

/** Nilai band terinterpolasi untuk posisi m (0 = bass .. 1 = treble). */
private fun band(f:VizFrame,m:Float,ex:Float=1.25f):Float{
 val x=m.coerceIn(0f,1f)*(Spectrum.B-1)
 val i=x.toInt()
 val j=min(i+1,Spectrum.B-1)
 val fr=x-i
 val v=f.bands[i]*(1f-fr)+f.bands[j]*fr
 return v.coerceIn(0f,1f).pow(ex)
}

/**
 * A: gelombang melingkar mengelilingi cover. Pusat gelombang = pusat canvas = pusat cover (keduanya digambar
 * di Box yang sama), bass di bawah, treble di atas, simetris kiri-kanan.
 * [innerDp] = jari-jari dasar gelombang (jari-jari cover + sedikit jarak), [lenDp] = tinggi maksimum gelombang.
 */
@Composable fun VizRadial(f:VizFrame,modifier:Modifier,innerDp:Dp,lenDp:Dp){
 val cs=MaterialTheme.colorScheme
 val c1=cs.primary;val c2=cs.tertiary
 val mid=lerp(c1,c2,0.5f)
 val path=remember{Path()}
 val echo=remember{Path()}
 Canvas(modifier){
  f.tick.longValue
  val e=f.energy
  if(e<0.01f)return@Canvas
  val cx=size.width/2;val cy=size.height/2
  val r0=innerDp.toPx()
  val maxL=minOf(lenDp.toPx(),size.width/2-r0-3.dp.toPx()).coerceAtLeast(8.dp.toPx())
  val minL=1.5.dp.toPx()
  val n=240
  path.rewind();echo.rewind()
  for(i in 0..n){
   val u=i/n.toFloat()
   val th=(PI/2+u*2*PI).toFloat()
   // 0 = bass (bawah) .. 1 = treble (atas); kemiringan nol di kedua ujung -> kurva mulus tanpa sudut tajam
   val m=0.5f-0.5f*cos(u*2f*PI.toFloat())
   // sensitif: kurva diperlebar (pangkat < 1) lalu dikuatkan
   val v=(band(f,m,0.7f)*1.3f).coerceIn(0f,1f)
   val rip=1f+0.10f*sin(th*7f+f.time*2.0f)
   val l=minL+maxL*v*e*rip
   val l2=minL+maxL*0.65f*v*e*(1f+0.14f*sin(th*5f-f.time*1.6f+1.3f))
   val ca=cos(th);val sa=sin(th)
   val x=cx+ca*(r0+l);val y=cy+sa*(r0+l)
   val x2=cx+ca*(r0+l2);val y2=cy+sa*(r0+l2)
   if(i==0){path.moveTo(x,y);echo.moveTo(x2,y2)}else{path.lineTo(x,y);echo.lineTo(x2,y2)}
  }
  path.close();echo.close()
  val br=Brush.sweepGradient(0f to mid,0.25f to c1,0.5f to mid,0.75f to c2,1f to mid,center=Offset(cx,cy))
  drawPath(path,br,alpha=0.20f*e)                                   // isi lembut (tertutup cover di bagian tengah)
  drawPath(echo,br,alpha=0.45f*e,style=Stroke(1.dp.toPx(),join=StrokeJoin.Round))
  drawPath(path,br,alpha=e,style=Stroke(2.dp.toPx(),join=StrokeJoin.Round))
 }
}

/* ===== VISUALIZER B (DINONAKTIFKAN) =====
 * Tidak dipakai di pemutar saat ini. Untuk mengaktifkan lagi, lihat komentar di MainActivity.kt (cari "VIZ_COUNT").
 */
/** B: batang bulat tumbuh dari bawah ke atas, selebar layar tanpa margin; bass di kiri-kanan tengah, treble di tepi. */
@Composable fun VizBars(f:VizFrame,modifier:Modifier){
 val cs=MaterialTheme.colorScheme
 val c1=cs.primary;val c2=cs.tertiary
 Canvas(modifier){
  f.tick.longValue
  val e=f.energy
  if(e<0.01f)return@Canvas
  val n=41
  val bw=size.width/n*0.62f
  val pitch=(size.width-bw)/(n-1)   // batang pertama menempel kiri, terakhir menempel kanan
  val full=size.height*0.96f
  val minH=3.dp.toPx()
  val c=(n-1)/2f
  for(i in 0 until n){
   val m=abs(i-c)/c
   val h=minH+(full-minH)*band(f,m)*e
   drawRoundRect(lerp(c1,c2,m).copy(alpha=e),Offset(pitch*i,size.height-h),Size(bw,h),CornerRadius(bw/2))
  }
 }
}

private val WBASE=floatArrayOf(0.55f,0.62f,0.70f)
private val WAMP=floatArrayOf(1.0f,0.8f,0.6f)
private val WVK=floatArrayOf(2.4f,3.4f,4.8f)
private val WSPD=floatArrayOf(1.1f,-1.5f,2.0f)
private val WALPHA=floatArrayOf(0.28f,0.38f,0.6f)

/* ===== VISUALIZER C (DINONAKTIFKAN) =====
 * Tidak dipakai di pemutar saat ini. Untuk mengaktifkan lagi, lihat komentar di MainActivity.kt (cari "VIZ_COUNT").
 */
/** C: tiga gelombang transparan berlapis yang mengalir selebar area, naik-turun mengikuti spektrum. */
@Composable fun VizWaves(f:VizFrame,modifier:Modifier){
 val cs=MaterialTheme.colorScheme
 val cols=listOf(cs.primary,cs.tertiary,cs.secondary)
 val path=remember{Path()}
 Canvas(modifier){
  f.tick.longValue
  val e=f.energy
  if(e<0.01f)return@Canvas
  val w=size.width;val h=size.height
  val step=6.dp.toPx()
  for(L in 0 until 3){
   path.rewind()
   path.moveTo(0f,h)
   var x=0f
   while(true){
    val xr=(x/w).coerceIn(0f,1f)
    val m=abs(xr*2f-1f)
    val s=0.5f+0.5f*sin(WVK[L]*2f*PI.toFloat()*xr+f.time*WSPD[L]*2f)
    val y=h*WBASE[L]-WAMP[L]*0.5f*h*(0.15f+0.85f*band(f,m)*e)*(0.4f+0.6f*s)
    path.lineTo(x,y)
    if(x>=w)break
    x=min(x+step,w)
   }
   path.lineTo(w,h)
   path.close()
   drawPath(path,cols[L].copy(alpha=WALPHA[L]*e))
  }
 }
}
