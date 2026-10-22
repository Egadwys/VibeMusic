package com.ega.project.vibemusic

import android.Manifest.permission.*
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.view.WindowManager
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val t=android.graphics.Color.TRANSPARENT
  enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(t,t),navigationBarStyle=SystemBarStyle.auto(t,t))
  if(Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
  setContent{Root()}
 }
}

@Composable fun MusikTheme(mode:Int,content:@Composable () -> Unit){
 val dark=mode==2||(mode==0&&isSystemInDarkTheme())
 val ctx=LocalContext.current
 val darkOv:(ColorScheme)->ColorScheme={it.copy(background=Color.Black,surface=Color.Black,surfaceVariant=Color(0xFF161616),surfaceContainer=Color(0xFF0C0C0C))}
 // Material You (dynamic color) di Android 12+, fallback skema teal yang konsisten di bawahnya
 val cs=when{
  Build.VERSION.SDK_INT>=31->if(dark)darkOv(dynamicDarkColorScheme(ctx)) else dynamicLightColorScheme(ctx)
  dark->darkOv(darkColorScheme(primary=Color(0xFF80CBC4),secondaryContainer=Color(0xFF334B48),onSecondaryContainer=Color(0xFFCCE8E4),tertiary=Color(0xFF9DCAE7)))
  else->lightColorScheme(primary=Color(0xFF00796B),secondaryContainer=Color(0xFFCCE8E4),onSecondaryContainer=Color(0xFF00201D),tertiary=Color(0xFF456179))
 }
 val view=LocalView.current
 SideEffect{
  (view.context as? Activity)?.window?.let{w->
   val ic=WindowCompat.getInsetsController(w,view)
   ic.isAppearanceLightStatusBars=!dark;ic.isAppearanceLightNavigationBars=!dark
  }
 }
 MaterialTheme(cs,content=content)
}

@Composable fun Root(){
 val c=LocalContext.current
 val sp=c.getSharedPreferences("s",0)
 val asp=c.getSharedPreferences("AppState",Context.MODE_PRIVATE)
 var mode by remember{mutableIntStateOf(sp.getInt("mode",0))}
 var glass by remember{mutableStateOf(asp.getBoolean("glass_effect",false))}
 CompositionLocalProvider(LocalGlass provides glass){
  MusikTheme(mode){Surface(Modifier.fillMaxSize()){Perm{
   App(mode,{m->mode=m;sp.edit().putInt("mode",m).apply()},glass){g->glass=g;asp.edit().putBoolean("glass_effect",g).apply()}
  }}}
 }
}

// ===================== GLASS EFFECT (pengaturan) =====================
// Panel dibuat tembus pandang dengan tepi terang, di atas latar gradasi warna tema (layar utama) / cover yang di-blur (pemutar).
// Blur latar sungguhan: pemutar dan dialog (Android 12+ saja). Di bawah itu tampil versi tembus pandang tanpa blur.
val LocalGlass=compositionLocalOf{false}
private val glassEdge=Brush.linearGradient(listOf(Color.White.copy(alpha=0.45f),Color.White.copy(alpha=0.06f)))

@Composable fun glassColor(base:Color,alpha:Float=0.55f):Color=if(LocalGlass.current)base.copy(alpha=alpha) else base

/** warna latar dialog: tembus pandang saat glass aktif */
@Composable fun dlgColor():Color=
 if(LocalGlass.current)MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=0.72f) else AlertDialogDefaults.containerColor

/** kartu kaca (dipakai di tile); tanpa efek bila glass mati */
@Composable fun Modifier.glassCard(shape:Shape):Modifier{
 if(!LocalGlass.current)return this
 return this.background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=0.35f),shape).border(1.dp,glassEdge,shape)
}

/** latar layar utama saat glass aktif: tiga gumpalan warna tema yang lembut */
@Composable fun GlassBackdrop(){
 val cs=MaterialTheme.colorScheme
 val a=cs.primary;val b=cs.tertiary;val d=cs.secondary
 Canvas(Modifier.fillMaxSize()){
  fun blob(col:Color,cx:Float,cy:Float,r:Float){
   drawCircle(Brush.radialGradient(listOf(col.copy(alpha=0.42f),col.copy(alpha=0f)),Offset(cx,cy),r),r,Offset(cx,cy))
  }
  blob(a,size.width*0.10f,size.height*0.10f,size.width*0.95f)
  blob(b,size.width*0.98f,size.height*0.45f,size.width*0.85f)
  blob(d,size.width*0.15f,size.height*0.90f,size.width*0.95f)
 }
}

/** latar pemutar saat glass aktif: cover di-blur (Android 12+) atau gradasi warna tema, ditutup scrim agar teks tetap terbaca */
@Composable fun GlassCoverBackdrop(s:Song){
 val cs=MaterialTheme.colorScheme
 Box(Modifier.fillMaxSize()){
  if(Build.VERSION.SDK_INT>=31)Cover(s,Modifier.fillMaxSize().blur(56.dp).graphicsLayer(alpha=0.6f),shape=RectangleShape,px=240)
  else GlassBackdrop()
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cs.surface.copy(alpha=0.25f),cs.surface.copy(alpha=0.7f)))))
 }
}

/** blur latar di belakang jendela dialog (Android 12+, bila perangkat mengizinkan blur antar-jendela). Dipanggil di dalam isi dialog. */
@Composable fun GlassDialogBlur(){
 if(!LocalGlass.current||Build.VERSION.SDK_INT<31)return
 val w=(LocalView.current.parent as? DialogWindowProvider)?.window?:return
 SideEffect{
  runCatching{
   w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
   val lp=w.attributes;lp.blurBehindRadius=40;w.attributes=lp
  }
 }
}


@Composable fun Perm(content:@Composable () -> Unit){
 val c=LocalContext.current
 val need=if(Build.VERSION.SDK_INT>=33)arrayOf(READ_MEDIA_AUDIO,POST_NOTIFICATIONS) else arrayOf(READ_EXTERNAL_STORAGE)
 fun ok()=ContextCompat.checkSelfPermission(c,need[0])==PackageManager.PERMISSION_GRANTED
 var granted by remember{mutableStateOf(ok())}
 val l=rememberLauncherForActivityResult(RequestMultiplePermissions()){granted=ok()}
 LaunchedEffect(Unit){if(!granted)l.launch(need)}
 if(granted)content() else Box(Modifier.fillMaxSize(),Alignment.Center){Button({l.launch(need)}){Text("Izinkan akses musik")}}
}

/** Getaran lembut (tick) untuk interaksi. Mengikuti pengaturan "getaran sentuh" sistem. */
@Composable fun rememberTick():()->Unit{
 val v=LocalView.current
 return remember(v){{v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);Unit}}
}

fun fmt(ms:Long)="%d:%02d".format(ms/60000,ms/1000%60)

@Composable fun Vinyl(m:Modifier=Modifier){
 val bg=MaterialTheme.colorScheme.surfaceVariant
 Canvas(m.background(bg)){
  val r=size.minDimension*0.42f
  drawCircle(Color(0xFF0B0B0B),r,center)
  listOf(0.85f,0.68f,0.52f).forEach{drawCircle(Color(0x44FFFFFF),r*it,center,style=Stroke(size.minDimension*0.006f))}
  drawCircle(Color(0xFF26A69A),r*0.34f,center)
  drawCircle(Color(0xFF0B0B0B),r*0.07f,center)
 }
}

@Composable fun Cover(s:Song?,m:Modifier=Modifier,shape:Shape=RoundedCornerShape(14.dp),px:Int=320,zoom:Float=1f){
 val c=LocalContext.current
 val bmp by produceState<Bitmap?>(s?.let{Covers.peek(it)},s?.id){value=if(s==null)null else Covers.get(c,s,px)}
 Box(m.clip(shape).aspectRatio(1f)){
  val b=bmp
  if(b!=null){val ib=remember(b){b.asImageBitmap()};Image(ib,null,Modifier.fillMaxSize().graphicsLayer(scaleX=zoom,scaleY=zoom),contentScale=ContentScale.Crop)}
  else Vinyl(Modifier.fillMaxSize())
 }
}

// Harmonik yang dipakai visualizer: (frekuensi sudut, kecepatan putar integer -> loop mulus)
private val WK = intArrayOf(2, 3, 5, 8, 13)
private val WN = intArrayOf(1, 2, -3, 4, -6)

@Composable fun VisualizerWave(playing: Boolean, active: Boolean = true) {
 // energi turun halus saat pause; setelah habis, gelombang dilepas dari komposisi (tidak digambar & tidak dianimasi)
 val energy by animateFloatAsState(if (playing) 1f else 0f, tween(450), label = "e")
 if (active && (playing || energy > 0.01f)) WaveCanvas(energy)
}

@Composable private fun WaveCanvas(energy: Float) {
 val tr = rememberInfiniteTransition(label = "wave")
 val time by tr.animateFloat(0f, (2 * PI).toFloat(),
  infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "t")
 // rotasi sangat pelan: satu putaran penuh 60 detik (ubah angkanya untuk lebih cepat/lambat)
 val rot by tr.animateFloat(0f, 360f,
  infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "rot")
 val cs = MaterialTheme.colorScheme
 val c1 = cs.primary; val c2 = cs.tertiary; val c3 = cs.secondary
 val steps = 120; val rings = 32; val bands = 8; val per = rings / bands
 // tabel trigonometri dihitung sekali; per frame hanya perkalian/penjumlahan
 val tabs = remember {
  val cx = FloatArray(steps + 1); val sx = FloatArray(steps + 1)
  val sk = Array(WK.size) { FloatArray(steps + 1) }; val ck = Array(WK.size) { FloatArray(steps + 1) }
  for (i in 0..steps) {
   val a = i * 2f * PI.toFloat() / steps
   cx[i] = cos(a); sx[i] = sin(a)
   for (j in WK.indices) { sk[j][i] = sin(WK[j] * a); ck[j][i] = cos(WK[j] * a) }
  }
  arrayOf<Any>(cx, sx, sk, ck)
 }
 @Suppress("UNCHECKED_CAST")
 val cosA = tabs[0] as FloatArray
 val sinA = tabs[1] as FloatArray
 @Suppress("UNCHECKED_CAST")
 val sk = tabs[2] as Array<FloatArray>
 @Suppress("UNCHECKED_CAST")
 val ck = tabs[3] as Array<FloatArray>
 val path = remember { Path() }
 val amp = remember { FloatArray(WK.size) }
 val ph = remember { FloatArray(WK.size) }
 val sm = remember { FloatArray(WK.size) } // energi tiap harmonik dari audio asli (sudah dihaluskan)
 Canvas(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { rotationZ = rot }) {
  val cx = size.width / 2; val cy = size.height / 2
  val inner = 130.dp.toPx()
  val spread = 110.dp.toPx() // jarak jangkauan gelombang dari cover (naikkan/turunkan sesuai selera)
  val tm = time; val e = energy
  // tanpa denyut ritmis: amplitudo tiap harmonik berubah pelan -> bentuk terus bermorfosis
  amp[0] = 0.60f + 0.40f * sin(tm + 1f)
  amp[1] = 0.60f + 0.40f * sin(tm * 2f + 2f)
  amp[2] = 0.50f + 0.50f * sin(tm * 3f)
  amp[3] = (0.35f + 0.35f * sin(tm * 5f + 1f)) * 0.55f
  amp[4] = 0.15f * (1f + 0.5f * sin(tm * 4f))
  // Audio asli: 28 band spektrum dibagi ke 5 harmonik (bass -> harmonik rendah, treble -> tinggi).
  // Tidak ada data audio (mis. format tidak didukung)? Otomatis memakai animasi dekoratif.
  val live = Spectrum.live()
  val lv = Spectrum.levels
  var tot = 0f
  for (j in WK.indices) {
   val a0 = j * Spectrum.B / WK.size; val b0 = (j + 1) * Spectrum.B / WK.size
   var m = 0f
   for (k in a0 until b0) m += lv[k]
   m /= (b0 - a0)
   sm[j] = if (m > sm[j]) sm[j] + (m - sm[j]) * 0.6f else sm[j] * 0.9f
   tot += sm[j]
  }
  val lvl = tot / WK.size
  if (live) for (j in WK.indices) amp[j] *= 0.4f + 1.6f * sm[j]
  val gain = if (live) (0.30f + 1.6f * lvl).coerceAtMost(1.25f) else 1f
  val norm = amp.sum()
  // mengembang-mengempis perlahan (periode ~5,3 dtk, integer terhadap loop 16 dtk -> mulus)
  val boost = 1f + 0.12f * sin(tm * 3f)
  // cahaya lembut di belakang ring
  drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.22f * e), Color.Transparent),
   Offset(cx, cy), inner + spread * 1.1f), inner + spread * 1.1f, Offset(cx, cy))
  val stroke = Stroke(0.9.dp.toPx())
  for (bd in bands - 1 downTo 0) {
   path.rewind()
   for (q in 0 until per) {
    val t = (bd * per + q + 1f) / rings
    for (j in WK.indices) ph[j] = WK[j] * t * 1.5f + WN[j] * (tm - 0.7f * t)
    val ps0 = sin(ph[0]); val pc0 = cos(ph[0]); val ps1 = sin(ph[1]); val pc1 = cos(ph[1])
    val ps2 = sin(ph[2]); val pc2 = cos(ph[2]); val ps3 = sin(ph[3]); val pc3 = cos(ph[3])
    val ps4 = sin(ph[4]); val pc4 = cos(ph[4])
    val rMax = spread * t * 0.92f * boost
    for (i in 0..steps) {
     // sin(ka+φ) = sin(ka)cosφ + cos(ka)sinφ
     val w = amp[0] * (sk[0][i] * pc0 + ck[0][i] * ps0) + amp[1] * (sk[1][i] * pc1 + ck[1][i] * ps1) +
       amp[2] * (sk[2][i] * pc2 + ck[2][i] * ps2) + amp[3] * (sk[3][i] * pc3 + ck[3][i] * ps3) +
       amp[4] * (sk[4][i] * pc4 + ck[4][i] * ps4)
     val n = 0.5f + 0.5f * (w / norm)
     val r = inner + rMax * (0.10f + 0.90f * e * n * n * gain) // n^2 -> puncak lebih runcing
     val x = cx + r * cosA[i]; val y = cy + r * sinA[i]
     if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
   }
   val tb = (bd + 0.5f) / bands
   val col = if (tb < 0.5f) lerp(c1, c2, tb * 2f) else lerp(c2, c3, (tb - 0.5f) * 2f)
   drawPath(path, col.copy(alpha = (0.95f - 0.78f * tb) * e), style = stroke)
  }
 }
}

@Composable fun <T> Grid(items:List<T>,key:(T)->Any,tile:@Composable (Int,T)->Unit){
 LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),
  contentPadding=PaddingValues(start=8.dp,top=8.dp,end=8.dp,bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+96.dp),
  horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  itemsIndexed(items,key={_,t->key(t)}){i,t->tile(i,t)}
 }
}

@Composable fun Tile(cover:Song?,title:String,sub:String,menu:List<Pair<String,()->Unit>> = emptyList(),active:Boolean=false,onClick:()->Unit){
 var m by remember{mutableStateOf(false)}
 val tick=rememberTick()
 Column(Modifier.clip(RoundedCornerShape(14.dp)).glassCard(RoundedCornerShape(14.dp)).clickable(onClick={tick();onClick()}).padding(4.dp)){
  Cover(cover,Modifier.fillMaxWidth())
  Row(verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f).padding(top=6.dp)){
    Text(title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge,
     color=if(active)MaterialTheme.colorScheme.primary else Color.Unspecified, textAlign = TextAlign.Start)
    Text(sub,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
   }
   if(menu.isNotEmpty())Box{
    IconButton({tick();m=true},Modifier.size(28.dp)){Icon(Icons.Default.MoreVert,null,Modifier.size(18.dp))}
    DropdownMenu(m,{m=false}){menu.forEach{(t,a)->DropdownMenuItem(text={Text(t)},onClick={tick();a();m=false})}}
   }
  }
 }
}

@Composable fun SongGrid(list:List<Song>,nowId:String?,pl:Playlists,from:String?,onNext:(Song)->Unit,onAdd:(Song)->Unit,onPlay:(Int)->Unit){
 val names=pl.map.keys.sorted()
 Grid(list,{it.id}){i,s->
  val menu:List<Pair<String,()->Unit>> = listOf<Pair<String,()->Unit>>("Putar berikutnya" to {onNext(s)},"Tambah ke antrean" to {onAdd(s)})+
   names.map{n->"Tambah ke $n" to {pl.add(n,s.id)}}+
   (if(from!=null)listOf("Hapus dari playlist" to {pl.remove(from,s.id)}) else emptyList())
  Tile(s,s.title,s.artist,menu,s.id.toString()==nowId){onPlay(i)}
 }
}

@Composable fun Groups(g:Map<String,List<Song>>,open:(String)->Unit){
 val keys=remember(g){g.keys.sorted()}
 val pick=remember(g){g.mapValues{it.value.random()}}
 Grid(keys,{it}){_,k->Tile(pick[k],k,"${g.getValue(k).size} lagu"){open(k)}}
}

/** kunci detail untuk daftar YouTube Trending di Home (tampil sebagai tile pertama di grid selama streaming YouTube aktif) */
const val YT_DETAIL="y:YouTube Trending"

/**
 * Home = grid folder. Bila streaming YouTube aktif ([yt] != null), tile pertama selalu daftar YouTube Trending,
 * dan tarik ke bawah (swipe down) memuat ulang daftar itu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeTab(g:Map<String,List<Song>>,yt:YtState?,open:(String)->Unit,openYt:()->Unit){
 if(yt==null&&g.isEmpty()){Box(Modifier.fillMaxSize(),Alignment.Center){Text("Tidak ada lagu di folder ini")};return}
 val keys=remember(g){g.keys.sorted()}
 val pick=remember(g){g.mapValues{it.value.random()}}
 val items=if(yt!=null)listOf(YT_DETAIL)+keys else keys
 val grid:@Composable ()->Unit={
  Grid(items,{it}){_,k->
   if(k==YT_DETAIL&&yt!=null){
    val l=yt.trending
    Tile(l.firstOrNull(),"YouTube Trending",when{l.isNotEmpty()->"${l.size} lagu";yt.busy->"Memuat…";else->"Gagal memuat · ketuk untuk coba lagi"}){
     if(l.isEmpty())yt.loadTrending(true) else openYt()
    }
   } else Tile(pick[k],k,"${g.getValue(k).size} lagu"){open(k)}
  }
 }
 if(yt!=null)PullToRefreshBox(isRefreshing=yt.busy,onRefresh={yt.loadTrending(true)},modifier=Modifier.fillMaxSize()){grid()} else grid()
}

@Composable fun PlaylistTab(pl:Playlists,byId:Map<Long,Song>,open:(String)->Unit){
 var dlg by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")}
 val tick=rememberTick()
 Column{
  FilledTonalButton({tick();dlg=true},Modifier.padding(horizontal=12.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Playlist baru")}
  Grid(pl.map.keys.sorted(),{it}){_,n->
   val ids=pl.map[n].orEmpty()
   Tile(ids.firstNotNullOfOrNull{byId[it]},n,"${ids.size} lagu",listOf("Hapus" to {pl.delete(n)})){open(n)}
  }
 }
 if(dlg)AlertDialog(containerColor=dlgColor(),onDismissRequest={dlg=false},confirmButton={TextButton({pl.create(name.trim());name="";dlg=false}){Text("Buat")}},
  title={Text("Playlist baru")},text={OutlinedTextField(name,{name=it},singleLine=true,label={Text("Nama")})})
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun App(mode:Int,onMode:(Int)->Unit,glass:Boolean,onGlass:(Boolean)->Unit){
 val c=LocalContext.current
 val tick=rememberTick()
 val appSp = c.getSharedPreferences("AppState", Context.MODE_PRIVATE) 
 
 LaunchedEffect(Unit){ appSp.edit().remove("selected_folder_uri").apply() }
 // sebelum pengguna memilih folder untuk pertama kali, perpustakaan dikosongkan dulu (dialog pilihan folder tampil)
 remember{Online.init(c);Yt.init(c)}
 // mode streaming YouTube: saklar di pengaturan; bila aktif, daftar YouTube Trending muncul di Home
 var ytOn by remember{mutableStateOf(appSp.getBoolean("yt_enabled",false))}
 var ytMax by remember{mutableStateOf(appSp.getBoolean("yt_max_quality",false))}
 var songs by remember{mutableStateOf(if(Folders.prompted(c))loadSongs(c) else emptyList())}
 var folders by remember{mutableStateOf(Folders.get(c))}
 var reloadKey by remember{mutableIntStateOf(0)}
 var showSettings by remember{mutableStateOf(false)}
 var showFirstRun by remember{mutableStateOf(!Folders.prompted(c))}
 fun reloadSongs(){folders=Folders.get(c);songs=loadSongs(c);reloadKey++}
 val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
  val first=!Folders.prompted(c)
  if(uri!=null)Folders.fromTree(uri)?.let{id->if(Folders.path(id).isEmpty())Folders.clear(c) else Folders.add(c,id)}
  Folders.markPrompted(c)
  reloadSongs()
  // setelah memilih folder pertama kali, buka pengaturan agar bisa menambah folder lain
  if(first&&uri!=null)showSettings=true
 }

 val pl=remember{Playlists(c)}
 var searching by remember{mutableStateOf(false)}
 var query by remember{mutableStateOf("")}
 var showSleep by remember{mutableStateOf(false)}
 var showEq by remember{mutableStateOf(false)}
 var showLyrics by remember{mutableStateOf(false)}
 var queueOpen by remember{mutableStateOf(false)}   // drawer antrean terbuka (visualizer dijeda supaya scroll mulus)
 var qv by remember{mutableIntStateOf(0)}   // naik tiap antrean berubah, memicu UI antrean menyegarkan diri
 var sleepEnd by remember{mutableLongStateOf(Sleep.end(c))}
 var sleepNow by remember{mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(Unit){while(true){sleepEnd=Sleep.end(c);delay(1000)}}
 LaunchedEffect(showSleep){while(showSleep){sleepNow=System.currentTimeMillis();delay(1000)}}
 var ctrl by remember{mutableStateOf<MediaController?>(null)}
 var nowId by remember{mutableStateOf<String?>(null)}
 var playing by remember{mutableStateOf(false)}
 var shuffle by remember{mutableStateOf(false)}
 var repeat by remember{mutableIntStateOf(Player.REPEAT_MODE_OFF)}
 val pos=remember{mutableStateOf(0L)}
 var sheetP by remember{mutableFloatStateOf(0f)}   // 0 = tersembunyi, 1 = terbuka penuh
 var hPx by remember{mutableFloatStateOf(3000f)}
 var sheetJob by remember{mutableStateOf<Job?>(null)}
 val expanded by remember{derivedStateOf{sheetP>0.5f}}
 val sheetVisible by remember{derivedStateOf{sheetP>0f}}
 
 // Stay awake: layar tetap menyala selama musik diputar, dipicu dengan menekan cover di pemutar
 var stayAwake by remember{mutableStateOf(appSp.getBoolean("stay_awake",false))}
 // Pop-up penjelasan stay awake, hanya muncul sekali (saat pertama kali diaktifkan)
 var showStayAwakeHint by remember{mutableStateOf(false)}
 val rootView=LocalView.current
 LaunchedEffect(stayAwake){appSp.edit().putBoolean("stay_awake",stayAwake).apply()}
 DisposableEffect(stayAwake,playing){
  rootView.keepScreenOn=stayAwake&&playing
  onDispose{rootView.keepScreenOn=false}
 }
 DisposableEffect(stayAwake){
  if(stayAwake)StayAwake.show(c) else StayAwake.hide(c)
  onDispose{StayAwake.hide(c)}
 }
 var tab by remember{mutableIntStateOf(appSp.getInt("tab", 0).let{if(it>=4)0 else it})}
 var detail by remember{mutableStateOf<String?>(appSp.getString("detail", null))}
 val pagerState = rememberPagerState(initialPage = tab) { 4 }

 LaunchedEffect(tab, detail) { appSp.edit().putInt("tab", tab).putString("detail", detail).apply() }
 val scope = rememberCoroutineScope()
 val ytState = remember{YtState(scope)}
 var navTarget by remember{mutableStateOf<Int?>(null)}
 var navJob by remember{mutableStateOf<Job?>(null)}
 // swipe -> update tab (diabaikan selama perpindahan dari tap navbar sedang berjalan)
 LaunchedEffect(pagerState) { snapshotFlow{pagerState.settledPage}.collect{ if(navTarget==null){ if(tab!=it) tick(); tab = it } } }
 fun selectTab(i:Int){
  searching = false; query = ""
  val wasDetail = detail != null
  if(!wasDetail && navTarget==i) return          // sudah menuju tab ini
  tab = i; detail = null; navTarget = i
  navJob?.cancel()                               // batalkan animasi sebelumnya, cegah tumpang tindih
  navJob = scope.launch{
   try{
    if(wasDetail) pagerState.scrollToPage(i)
    else{
     val from = pagerState.currentPage
     // lompat ke halaman sebelah dulu agar halaman perantara tidak ikut dirender
     if(kotlin.math.abs(i-from) > 1) pagerState.scrollToPage(if(i>from) i-1 else i+1)
     pagerState.animateScrollToPage(i, animationSpec = tween(280, easing = FastOutSlowInEasing))
    }
   } finally { if(navTarget==i) navTarget = null }
  }
 }

 var restored by remember{mutableStateOf(false)}
 fun savePos(){
  if(!restored)return
  val p=ctrl?:return
  runCatching{
   val id=p.currentMediaItem?.mediaId?:return
   appSp.edit().putString("last_id",id).putLong("last_pos",p.currentPosition).putInt("last_idx",p.currentMediaItemIndex).apply()
  }
 }

 val lifecycleOwner = LocalLifecycleOwner.current
 DisposableEffect(lifecycleOwner) {
  val observer = LifecycleEventObserver { _, event ->
   if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) savePos()
  }
  lifecycleOwner.lifecycle.addObserver(observer)
  onDispose { savePos(); lifecycleOwner.lifecycle.removeObserver(observer) }
 }

 // simpan posisi saat pause / ganti lagu, dan tiap detik saat sedang diputar
 LaunchedEffect(playing, nowId) { savePos() }
 LaunchedEffect(playing, ctrl) { while(playing){ delay(1000); savePos() } }

 LaunchedEffect(reloadKey){
  val upd=HashMap<Long,Song>();var n=0
  for(s in songs.toList()){
   if((s.artist==UA||s.album==UB)&&!Meta.tried(c,s.id)){
    val r=Meta.fetch(c,s)
    if(r!=null&&r!=s){upd[s.id]=r;n++}
    if(n>=15){val u=HashMap(upd);songs=songs.map{u[it.id]?:it};n=0}
    delay(1500)
   }
  }
  if(upd.isNotEmpty())songs=songs.map{upd[it.id]?:it}
 }
 
 DisposableEffect(Unit){
  val f=MediaController.Builder(c,SessionToken(c,ComponentName(c,PlaybackService::class.java))).buildAsync()
  f.addListener({ctrl=f.get()},ContextCompat.getMainExecutor(c))
  onDispose{MediaController.releaseFuture(f)}
 }
 
 DisposableEffect(ctrl){
  val p=ctrl?:return@DisposableEffect onDispose{}
  nowId=p.currentMediaItem?.mediaId;playing=p.isPlaying;shuffle=p.shuffleModeEnabled;repeat=p.repeatMode;qv++
  val l=object:Player.Listener{override fun onEvents(q:Player,e:Player.Events){
   nowId=q.currentMediaItem?.mediaId;playing=q.isPlaying;shuffle=q.shuffleModeEnabled;repeat=q.repeatMode
   if(e.contains(Player.EVENT_TIMELINE_CHANGED)||e.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))qv++}}
  p.addListener(l);onDispose{p.removeListener(l)}
 }
 
 LaunchedEffect(ctrl){while(true){pos.value=ctrl?.currentPosition?:0L;delay(250)}}

 val byId=remember(songs,Online.version){songs.associateBy{it.id}+Online.saved}
 
 LaunchedEffect(ctrl) {
  val p = ctrl ?: return@LaunchedEffect
  if (p.mediaItemCount == 0) {
   // pulihkan mode shuffle & repeat yang tersimpan
   val sSaved = appSp.getBoolean("shuffle", false)
   val rSaved = appSp.getInt("repeat", Player.REPEAT_MODE_OFF)
   p.shuffleModeEnabled = sSaved
   p.repeatMode = rSaved
   shuffle = sSaved
   repeat = rSaved

   val lastId = appSp.getString("last_id", null)
   val lastSong = byId[lastId?.toLongOrNull() ?: 0L]
   if (lastSong != null) {
    val lastPos = appSp.getLong("last_pos", 0L)
    // pulihkan seluruh antrean, bukan hanya satu lagu, supaya next/prev dan repeat tetap jalan
    val queue = appSp.getString("queue", null)
     ?.split(',')?.mapNotNull { it.toLongOrNull() }?.mapNotNull { byId[it] }.orEmpty()
    val sIdx = appSp.getInt("last_idx", -1)
    val idx = if (sIdx in queue.indices && queue[sIdx].id == lastSong.id) sIdx else queue.indexOfFirst { it.id == lastSong.id }
    if (idx >= 0) p.setMediaItems(queue.map { it.item() }, idx, lastPos)
    else p.setMediaItem(lastSong.item(), lastPos)
    p.prepare()
   }
  }
  restored = true
 }

 // simpan shuffle & repeat setiap kali berubah (setelah pemulihan selesai)
 LaunchedEffect(shuffle, repeat) {
  if (restored) appSp.edit().putBoolean("shuffle", shuffle).putInt("repeat", repeat).apply()
 }

 fun animateSheet(target:Float,vel:Float=0f){
  sheetJob?.cancel()
  sheetJob=scope.launch{
   animate(sheetP,target,vel,spring(dampingRatio=0.9f,stiffness=Spring.StiffnessMedium)){v,_->sheetP=v.coerceIn(0f,1f)}
  }
 }
  LaunchedEffect(ytOn){
  // streaming aktif -> muat daftar Trending; dimatikan saat daftarnya sedang dibuka -> kembali ke Home
  if(ytOn)ytState.loadTrending() else if(detail?.startsWith("y:")==true)detail=null
 }
 BackHandler(expanded||searching||detail!=null){if(expanded)animateSheet(0f) else if(searching){searching=false;query=""} else detail=null}
 fun play(l:List<Song>,i:Int){
  // simpan antrean agar bisa dipulihkan setelah aplikasi ditutup
  appSp.edit().putString("queue",l.joinToString(","){it.id.toString()}).apply()
  l.forEach{if(it.id<0)Online.persist(it)}
  ctrl?.run{setMediaItems(l.map{it.item()},i,0L);prepare();play()}
 }
 // antrean bisa diubah (tambah/hapus), jadi daftar id disimpan ulang dari isi player
 fun saveQueue(){
  val p=ctrl?:return
  appSp.edit().putString("queue",(0 until p.mediaItemCount).joinToString(","){p.getMediaItemAt(it).mediaId}).apply()
 }
 fun playNext(s:Song){
  val p=ctrl?:return
  if(p.mediaItemCount==0){play(listOf(s),0);return}
  if(s.id<0)Online.persist(s)
  p.addMediaItem(p.currentMediaItemIndex+1,s.item());saveQueue()
 }
 fun enqueue(s:Song){
  val p=ctrl?:return
  if(p.mediaItemCount==0){play(listOf(s),0);return}
  if(s.id<0)Online.persist(s)
  p.addMediaItem(s.item());saveQueue()
 }
 val cur=remember(byId,nowId){nowId?.toLongOrNull()?.let{byId[it]}}
 val dragState = rememberDraggableState{ dy -> sheetP = (sheetP - dy/hPx).coerceIn(0f,1f) }
 fun settle(v:Float){
  val target = when{ v < -700f -> 1f; v > 700f -> 0f; sheetP>0.5f -> 1f; else -> 0f }
  if((target==1f)!=expanded) tick()
  animateSheet(target, -v/hPx)
 }
 // geser naik: buka pemutar, geser turun: tutup (mengikuti jari)
 val dragMod = Modifier.draggable(dragState, Orientation.Vertical, enabled = cur!=null,
  onDragStarted = { sheetJob?.cancel() }, onDragStopped = { settle(it) })
 val tabTitles=listOf("Home","Album","Artis","Playlist")
 var showSong by remember{mutableStateOf(false)}
 LaunchedEffect(nowId,playing){
  if(playing&&nowId!=null){showSong=true;delay(2000);showSong=false} else showSong=false
 }
 val title=if(showSong&&cur!=null)cur.title else (detail?.drop(2)?:tabTitles[tab])
 val byFolder=remember(songs){songs.filter{it.folder!=null}.groupBy{it.folder!!}}
 val byAlbum=remember(songs){songs.groupBy{it.album}}
 val byArtist=remember(songs){songs.groupBy{it.artist}}

 Box(Modifier.fillMaxSize().onSizeChanged{ hPx = it.height.toFloat() }){
  if(glass)GlassBackdrop()
  Scaffold(topBar={
   TopAppBar(
    title={
     if(searching){
      val fr=remember{FocusRequester()}
      LaunchedEffect(Unit){runCatching{fr.requestFocus()}}
      TextField(query,{query=it},Modifier.fillMaxWidth().focusRequester(fr),singleLine=true,
       placeholder={Text("Cari judul, artis, album…")},
       colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
        focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
     } else AnimatedContent(targetState=title,label="title"){t->Text(t,maxLines=1,overflow=TextOverflow.Ellipsis)}
    },
    navigationIcon={
     if(searching)IconButton({tick();searching=false;query=""}){Icon(Icons.AutoMirrored.Filled.ArrowBack,null)}
     else if(detail!=null)IconButton({tick();detail=null}){Icon(Icons.AutoMirrored.Filled.ArrowBack,null)}
    },
    actions={
     if(searching){
      if(query.isNotEmpty())IconButton({tick();query=""}){Icon(Icons.Default.Close,null)}
     } else {
      IconButton({tick();searching=true}){Icon(Icons.Default.Search,null)}
      IconButton({tick();showSettings=true}){Icon(Icons.Default.Settings,"Pengaturan")}
     }
    },
    colors=if(glass)TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent,scrolledContainerColor=Color.Transparent) else TopAppBarDefaults.topAppBarColors()
   )
  },
  containerColor=Color.Transparent,
  bottomBar={FloatingNav(tab,cur,playing,pos,ctrl,dragMod){selectTab(it)}}){pad->
  
  if(searching){
   val q=query.trim()
   val res=remember(songs,q){
    if(q.isEmpty())emptyList() else songs.filter{it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)}
     .sortedByDescending{it.title.startsWith(q,true)}
   }
   Box(Modifier.padding(top=pad.calculateTopPadding()).fillMaxSize()){
    when{
     q.isEmpty()->Box(Modifier.fillMaxSize(),Alignment.Center){Text("Ketik untuk mencari lagu",color=MaterialTheme.colorScheme.onSurfaceVariant)}
     res.isEmpty()->Box(Modifier.fillMaxSize(),Alignment.Center){Text("Tidak ada hasil untuk \"$q\"",color=MaterialTheme.colorScheme.onSurfaceVariant)}
     else->SongGrid(res,nowId,pl,null,{playNext(it)},{enqueue(it)}){play(res,it)}
    }
   }
  } else if(detail!=null){
   val d = detail!!
   val n=d.drop(2)
   val ids=if(d[0]=='p')pl.map[n] else null
   val yt=ytState.trending
   val list=remember(d,songs,ids,yt){when(d[0]){
    'f'->songs.filter{it.folder==n};'a'->songs.filter{it.album==n};'r'->songs.filter{it.artist==n}
    'y'->yt
    else->ids.orEmpty().mapNotNull{byId[it]}}}
   Box(Modifier.padding(top=pad.calculateTopPadding()).fillMaxSize().pointerInput(Unit){ 
    detectHorizontalDragGestures { _, dragAmount -> if (dragAmount > 40) detail = null }
   }) {
    if(d[0]=='y'){
     // daftar YouTube: ketuk lagu = putar satu lagu saja (tanpa mengisi antrean); "Putar semua" mengantrekan seluruh daftar
     Column(Modifier.fillMaxSize()){
      if(list.isNotEmpty())FilledTonalButton({tick();play(list,0)},Modifier.padding(horizontal=12.dp)){
       Icon(Icons.Default.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Putar semua")
      }
      Box(Modifier.weight(1f)){SongGrid(list,nowId,pl,null,{playNext(it)},{enqueue(it)}){play(listOf(list[it]),0)}}
     }
    } else SongGrid(list,nowId,pl,if(d[0]=='p')n else null,{playNext(it)},{enqueue(it)}){play(list,it)}
   }
  } else {
   HorizontalPager(state = pagerState, modifier = Modifier.padding(top=pad.calculateTopPadding()).fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
    when(page){
     0->HomeTab(byFolder,if(ytOn)ytState else null,{detail="f:$it"}){detail=YT_DETAIL}
     1->Groups(byAlbum){detail="a:$it"}
     2->Groups(byArtist){detail="r:$it"}
     else->PlaylistTab(pl,byId){detail="p:$it"}
    }
   }
  }
 }
 
 // Now sudah dikomposisi sebelumnya, jadi swipe pertama tidak ada jeda; hanya digeser lewat graphicsLayer
 if(cur!=null){
  Box(Modifier.fillMaxSize().graphicsLayer{
   val p = sheetP
   translationY = (1f-p)*hPx
   alpha = if(p>0f) 1f else 0f
  }){
   QueueDrawerHost(ctrl,byId,nowId,qv,{saveQueue()},{queueOpen=it}){
   Now(cur,ctrl,playing,pos,shuffle,repeat,sheetVisible,queueOpen||showLyrics,dragMod,stayAwake,{
    val turnOn=!stayAwake
    stayAwake=turnOn
    // pop-up cara pakai, hanya sekali: saat stay awake pertama kali diaktifkan
    if(turnOn&&!appSp.getBoolean("stay_awake_hint_shown",false)){
     appSp.edit().putBoolean("stay_awake_hint_shown",true).apply()
     showStayAwakeHint=true
    }
   },sleepEnd,{showSleep=true},{showEq=true},{showLyrics=true}){ animateSheet(0f) }
   }
  }
 }

 if(showStayAwakeHint)AlertDialog(containerColor=dlgColor(),
  onDismissRequest={showStayAwakeHint=false},
  title={Text("Stay awake aktif")},
  text={Text("Layar akan tetap menyala selama musik diputar. Tekan cover di pemutar kapan saja untuk menyalakan atau mematikan fitur ini.")},
  confirmButton={TextButton({tick();showStayAwakeHint=false}){Text("Mengerti")}}
 )
 PlayerError.text?.let{err->
  // net = lagu YouTube gagal dimuat meski sudah diulang dan dilewati -> pesan masalah jaringan, detail teknis tetap bisa disalin
  val net=PlayerError.net
  AlertDialog(containerColor=dlgColor(),
   onDismissRequest={PlayerError.clear()},
   title={Text(if(net)"Gagal memuat lagu" else "Gagal memutar")},
   text={Column{
    GlassDialogBlur()
    if(net)Text("Lagu tidak dapat dimuat meski sudah dicoba ulang dan dilewati. Kemungkinan ada masalah jaringan, periksa koneksi internet kamu lalu coba lagi.",Modifier.padding(bottom=12.dp))
    Box(Modifier.heightIn(max=320.dp)){SelectionContainer{
     Text(err,Modifier.verticalScroll(rememberScrollState()),fontSize=11.sp,lineHeight=15.sp,fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace)}}
   }},
   confirmButton={TextButton({tick()
    (c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
     .setPrimaryClip(android.content.ClipData.newPlainText("error",err))}){Text("Salin")}},
   dismissButton={TextButton({tick();PlayerError.clear()}){Text("Tutup")}}
  )
 }
 if(showLyrics&&cur!=null)LyricsSheet(cur,pos){showLyrics=false}
 if(showSleep)SleepDialog(sleepEnd,sleepNow,{m->Sleep.set(c,m);sleepEnd=Sleep.end(c);showSleep=false},{showSleep=false})
 if(showEq)EqDialog{showEq=false}

 if(showFirstRun)AlertDialog(containerColor=dlgColor(),
  onDismissRequest={},
  title={Text("Pilih folder musik")},
  text={Text("Pilih folder yang ingin dipindai sebagai sumber musik. Kamu bisa memilih lebih dari satu folder dan mengubahnya kapan saja lewat tombol pengaturan.")},
  confirmButton={TextButton({tick();showFirstRun=false;folderPicker.launch(null)}){Text("Pilih folder")}},
  dismissButton={TextButton({tick();showFirstRun=false;Folders.markPrompted(c);reloadSongs()}){Text("Pindai semua folder")}}
 )
 if(showSettings)SettingsDialog(mode,onMode,ytOn,{on->ytOn=on;appSp.edit().putBoolean("yt_enabled",on).apply()},ytMax,{m->ytMax=m;appSp.edit().putBoolean("yt_max_quality",m).apply()},glass,onGlass,folders,{folderPicker.launch(null)},{Folders.remove(c,it);reloadSongs()},{Folders.clear(c);reloadSongs()},{showSettings=false})
 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun FloatingNav(tab:Int,cur:Song?,playing:Boolean,pos:State<Long>,ctrl:MediaController?,dragMod:Modifier,onSelect:(Int)->Unit){
 val items=listOf("Home" to Icons.Default.Home,"Album" to Icons.Default.Album,"Artis" to Icons.Default.Person,"Playlist" to Icons.AutoMirrored.Filled.QueueMusic)
 val iw=56.dp
 val glass=LocalGlass.current
 val tick=rememberTick()
 Box(dragMod.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp,vertical=12.dp),contentAlignment=Alignment.Center){
  Surface(shape=RoundedCornerShape(50),color=glassColor(MaterialTheme.colorScheme.surfaceContainerHigh,0.55f),tonalElevation=0.dp,shadowElevation=if(glass)0.dp else 10.dp,
   border=if(glass)BorderStroke(1.dp,glassEdge) else null){
   Row(Modifier.animateContentSize().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){
    items.forEachIndexed{i,(t,ic)->
     val sel=tab==i
     Box(Modifier.width(iw).height(48.dp).clip(CircleShape)
      .background(if(sel)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
      .clickable{tick();onSelect(i)},contentAlignment=Alignment.Center){
      Icon(ic,t,tint=if(sel)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
    if(cur!=null){
     Spacer(Modifier.width(4.dp))
     // tombol play/pause dengan cover art sebagai background (tap: play/pause, geser naik: buka pemutar)
     Box(Modifier.size(48.dp).clip(CircleShape).clickable{tick();if(playing)ctrl?.pause() else ctrl?.play()},contentAlignment=Alignment.Center){
      Cover(cur,Modifier.fillMaxSize().padding(4.dp),shape=CircleShape,px=200)
      Box(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha=0.35f)))
      Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=Color.White)
      CircularProgressIndicator(progress={(pos.value.toFloat()/maxOf(cur.dur,1L)).coerceIn(0f,1f)},
       modifier=Modifier.fillMaxSize(),strokeWidth=2.dp,color=MaterialTheme.colorScheme.primary,trackColor=Color.Transparent)
     }
    }
   }
  }
 }
}

@Composable fun Seek(pos:State<Long>,dur:Long,ctrl:MediaController?){
 val d=maxOf(dur,1L).toFloat()
 val p=pos.value
 val tick=rememberTick()
 Slider(p.toFloat().coerceIn(0f,d),{ctrl?.seekTo(it.toLong())},valueRange=0f..d,onValueChangeFinished={tick()})
 Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween){Text(fmt(p));Text(fmt(dur))}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Now(s:Song,ctrl:MediaController?,playing:Boolean,pos:State<Long>,shuffle:Boolean,repeat:Int,visible:Boolean,overlay:Boolean,dragMod:Modifier,stayAwake:Boolean,onStayAwake:()->Unit,sleepEnd:Long,onSleep:()->Unit,onEq:()->Unit,onLyrics:()->Unit,close:()->Unit){
 var lines by remember(s.id){mutableStateOf<List<Line>?>(null)}
 var showInfo by remember{mutableStateOf(false)}   // tekan lama cover = kartu info metadata lagu
 val ctx=LocalContext.current
 LaunchedEffect(s.id){lines=Lyrics.get(ctx,s)}
 val idx by remember(s.id){derivedStateOf{lines?.indexOfLast{it.ms<=pos.value}?:-1}}
 val on=MaterialTheme.colorScheme.primary;val off=MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f)
 
 val tick=rememberTick()
 // pilihan visualizer (tersimpan): 0 = A (gelombang melingkar), 1 = animasi gelombang berlapis.
 // Visualizer B dan C dinonaktifkan; cara mengaktifkan lagi ada di komentar "VIZ_COUNT" di bawah.
 val vsp=remember{ctx.getSharedPreferences("s",0)}
 val VIZ_COUNT=2   // jumlah visualizer aktif. Bila B & C diaktifkan lagi: ubah jadi 6 (A, animasi, B, C, B tanpa cover, C tanpa cover)
 var viz by remember{mutableIntStateOf(vsp.getInt("viz_mode",0).coerceIn(0,VIZ_COUNT-1))}
 var vizOff by remember{mutableStateOf(vsp.getBoolean("viz_off",false))}   // tekan lama tombol visualizer = matikan/nyalakan
 // saat antrean/lirik terbuka, visualizer memudar lalu berhenti total (tidak menghabiskan frame) agar scroll mulus
 val vizPlaying=playing&&!overlay
 val vf=rememberViz(vizPlaying,visible&&!vizOff)
 Surface(Modifier.fillMaxSize().then(dragMod),shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)){Box(Modifier.fillMaxSize()){
  if(LocalGlass.current)GlassCoverBackdrop(s)
  Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal=24.dp)){
   
   BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)){
    val areaH=maxHeight;val areaW=maxWidth
    // tekan cover = aktif/nonaktifkan stay awake. Border cover selalu tampil dengan tampilan yang sama, tidak terikat status stay awake
    @Composable fun CoverArt(size:Dp){
     Box(Modifier.size(size).shadow(5.dp,CircleShape,clip=false,ambientColor=Color.Black.copy(alpha=0.6f),spotColor=Color.Black.copy(alpha=0.6f))
      .clip(CircleShape).combinedClickable(onClick={tick();onStayAwake()},onLongClick={tick();showInfo=true})
      .border(3.dp,on,CircleShape)){
      Cover(s, Modifier.fillMaxSize(), shape = CircleShape, px = 700, zoom = 1.12f)
     }
    }
    Crossfade(if(vizOff)-1 else viz,Modifier.fillMaxSize(),tween(400),label="viz"){m->
     Box(Modifier.fillMaxSize(),Alignment.Center){
      when(m){
       -1->CoverArt(280.dp)
       0->{
        // A: gelombang & cover berada di satu Box -> pusat gelombang pasti sama dengan pusat cover
        Box(Modifier.requiredSize(areaW),Alignment.Center){
         VizRadial(vf,Modifier.fillMaxSize(),144.dp,40.dp)   // 144dp = jari-jari cover (140dp) + jarak 4dp
         CoverArt(280.dp)
        }
       }
       1->{
        // Animasi gelombang berlapis (visualizer lama), juga satu pusat dengan cover
        Box(Modifier.requiredSize(areaW),Alignment.Center){
         VisualizerWave(vizPlaying,visible&&!vizOff)
         CoverArt(280.dp)
        }
       }
       // ===================== VISUALIZER B & C (DINONAKTIFKAN) =====================
       // Untuk mengaktifkan kembali: (1) ubah VIZ_COUNT di atas jadi 6, (2) hapus tanda "//" pada blok di bawah ini.
       // Fungsi VizBars dan VizWaves masih ada utuh di Viz.kt.
       // 2,3->{
       //  // B/C: overlay di belakang cover, selebar layar tanpa margin, di dasar area
       //  val vm=Modifier.align(Alignment.BottomCenter).requiredWidth(areaW+48.dp).height(minOf(areaH,320.dp))
       //  if(m==2)VizBars(vf,vm) else VizWaves(vf,vm)
       //  CoverArt(280.dp)
       // }
       // else->{
       //  // 4,5: tanpa cover, di dasar area di atas lirik
       //  val vm=Modifier.align(Alignment.BottomCenter).requiredWidth(areaW+48.dp).height(minOf(areaH,320.dp))
       //  if(m==4)VizBars(vf,vm) else VizWaves(vf,vm)
       // }
       // ============================================================================
       else->CoverArt(280.dp)
      }
     }
    }
   }

   Box(Modifier.fillMaxWidth().height(100.dp).pointerInput(Unit){detectTapGestures(onLongPress={tick();onLyrics()})}, Alignment.CenterStart){
    val ll=lines
    when{
     ll==null->Text("Memuat lirik…",color=off, textAlign = TextAlign.Start)
     ll.isEmpty()->Text("Lirik tidak ditemukan",color=off, textAlign = TextAlign.Start)
     else->AnimatedContent(
      targetState = idx,
      transitionSpec = {
       (fadeIn(tween(400)) + slideInVertically(tween(400)){ it/2 }).togetherWith(
        fadeOut(tween(400)) + slideOutVertically(tween(400)){ -it/2 })
      }, label = "lyric"
     ){ i ->
      Text(ll.getOrNull(i)?.text?.ifBlank{"♪"}?:"", fontSize=22.sp, lineHeight=30.sp, 
       fontWeight=FontWeight.Bold, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
     }
    }
   }
   
   Spacer(Modifier.height(12.dp))
   Text(s.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
   Text(s.artist,color=on,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
   Spacer(Modifier.height(16.dp))
   
   Seek(pos,s.dur,ctrl)
   Row(Modifier.fillMaxWidth().padding(bottom=16.dp),Arrangement.SpaceEvenly,Alignment.CenterVertically){
    IconButton({tick();ctrl?.let{it.shuffleModeEnabled=!it.shuffleModeEnabled}}){Icon(Icons.Default.Shuffle,null,tint=if(shuffle)on else off)}
    IconButton({tick();ctrl?.seekToPrevious()},Modifier.size(48.dp)){Icon(Icons.Default.SkipPrevious,null,Modifier.size(32.dp))}
    FilledIconButton({tick();if(playing)ctrl?.pause() else ctrl?.play()},Modifier.size(72.dp)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(40.dp))}
    IconButton({tick();ctrl?.seekToNext()},Modifier.size(48.dp)){Icon(Icons.Default.SkipNext,null,Modifier.size(32.dp))}
    IconButton({tick();ctrl?.let{it.repeatMode=when(it.repeatMode){Player.REPEAT_MODE_OFF->Player.REPEAT_MODE_ALL;Player.REPEAT_MODE_ALL->Player.REPEAT_MODE_ONE;else->Player.REPEAT_MODE_OFF}}}){
     Icon(if(repeat==Player.REPEAT_MODE_ONE)Icons.Default.RepeatOne else Icons.Default.Repeat,null,tint=if(repeat==Player.REPEAT_MODE_OFF)off else on)}
   }
  }
  // hitungan mundur sleep timer: tampil di kanan panah bawah selama timer aktif
  var nowMs by remember{mutableLongStateOf(System.currentTimeMillis())}
  val sleepActive=sleepEnd!=0L
  LaunchedEffect(sleepActive){while(sleepActive){nowMs=System.currentTimeMillis();delay(1000)}}
  Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton({tick();close()}){Icon(Icons.Default.KeyboardArrowDown,null)}
   // stay awake aktif: ikon mata di samping panah bawah
   if(stayAwake){Icon(Icons.Default.Visibility,"Stay awake aktif",Modifier.size(18.dp),tint=on);Spacer(Modifier.width(8.dp))}
   if(sleepActive){
    Icon(Icons.Default.Timer,null,Modifier.size(16.dp),tint=on);Spacer(Modifier.width(4.dp))
    Text(if(sleepEnd<0)"Akhir lagu" else fmt((sleepEnd-nowMs).coerceAtLeast(0L)),style=MaterialTheme.typography.labelLarge,color=on)
   }
  }
  Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)){
   IconButton({tick();onEq()}){Icon(Icons.Default.Equalizer,"Equalizer")}
   IconButton({tick();onSleep()}){Icon(Icons.Default.Timer,"Sleep timer",tint=if(sleepEnd!=0L)on else LocalContentColor.current)}
   Row(Modifier.height(48.dp).clip(CircleShape).pointerInput(Unit){detectTapGestures(
     onTap={tick()
      // ketuk saat mati = nyalakan lagi; selain itu ganti ke visualizer berikutnya
      if(vizOff)vizOff=false else viz=(viz+1)%VIZ_COUNT
      vsp.edit().putInt("viz_mode",viz).putBoolean("viz_off",vizOff).apply()},
     onLongPress={tick();vizOff=!vizOff;vsp.edit().putBoolean("viz_off",vizOff).apply()})}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Default.GraphicEq,"Ganti visualizer",tint=if(vizOff)LocalContentColor.current.copy(alpha=0.4f) else LocalContentColor.current)
   }
  }
  if(showInfo)SongInfoCard(s){showInfo=false}
 }}
}

/** Lirik layar penuh (tekan lama lirik di pemutar): rata tengah, baris aktif di tengah layar, fade atas/bawah, ukuran + / -. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LyricsSheet(s:Song,pos:State<Long>,onDismiss:()->Unit){
 val ctx=LocalContext.current
 val sp=remember{ctx.getSharedPreferences("s",0)}
 var size by remember{mutableIntStateOf(sp.getInt("lyric_size",26))}
 var lines by remember(s.id){mutableStateOf<List<Line>?>(null)}
 LaunchedEffect(s.id){lines=Lyrics.get(ctx,s)}
 val idx by remember(s.id){derivedStateOf{lines?.indexOfLast{it.ms<=pos.value}?:-1}}
 val on=MaterialTheme.colorScheme.primary
 val off=MaterialTheme.colorScheme.onSurface.copy(alpha=0.4f)
 val tick=rememberTick()
 val ls=rememberLazyListState()
 // gulir halus supaya baris aktif berada tepat di tengah layar
 LaunchedEffect(idx,size,lines){
  if(idx<0||lines.isNullOrEmpty())return@LaunchedEffect
  withFrameNanos{}
  if(ls.isScrollInProgress)return@LaunchedEffect   // pengguna sedang menggulir/fling: jangan ditarik balik
  if(ls.layoutInfo.visibleItemsInfo.none{it.index==idx}){ls.scrollToItem(idx);withFrameNanos{}}
  val li=ls.layoutInfo
  val info=li.visibleItemsInfo.firstOrNull{it.index==idx}?:return@LaunchedEffect
  val center=(li.viewportStartOffset+li.viewportEndOffset)/2f
  val d=info.offset+info.size/2f-center
  if(d>1f||d<-1f)ls.animateScrollBy(d,tween(450))
 }
 fun setSize(v:Int){size=v.coerceIn(16,48);sp.edit().putInt("lyric_size",size).apply()}
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface){
  BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()){
   val ll=lines
   when{
    ll==null->Text("Memuat lirik…",Modifier.align(Alignment.Center),color=off)
    ll.isEmpty()->Text("Lirik tidak ditemukan",Modifier.align(Alignment.Center),color=off)
    else->LazyColumn(
     state=ls,
     modifier=Modifier.fillMaxSize(),
     contentPadding=PaddingValues(vertical=maxHeight/2),
     horizontalAlignment=Alignment.CenterHorizontally
    ){
     itemsIndexed(ll,contentType={_,_->0}){i,ln->
      val col by animateColorAsState(if(i==idx)on else off,tween(300),label="lyricColor")
      Text(ln.text.ifBlank{"♪"},color=col,fontSize=size.sp,lineHeight=(size*1.35f).sp,fontWeight=FontWeight.Bold,
       textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=(size/2.5f).dp))
     }
    }
   }
   // fade atas/bawah: gradien warna latar biasa (jauh lebih ringan daripada layer offscreen + blend mode); tidak menangkap sentuhan
   val bg=MaterialTheme.colorScheme.surface
   Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(maxHeight*0.2f).background(Brush.verticalGradient(listOf(bg,Color.Transparent))))
   Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight*0.2f).background(Brush.verticalGradient(listOf(Color.Transparent,bg))))
   Row(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    FilledTonalIconButton({tick();setSize(size-2)},enabled=size>16){Icon(Icons.Default.Remove,"Perkecil lirik")}
    FilledTonalIconButton({tick();setSize(size+2)},enabled=size<48){Icon(Icons.Default.Add,"Besarkan lirik")}
   }
  }
 }
}

/** Antrean pemutaran: ketuk lagu untuk melompat ke sana, ✕ untuk menghapus dari antrean. */
@Composable fun QueuePanel(ctrl:MediaController?,byId:Map<Long,Song>,nowId:String?,qv:Int,onChanged:()->Unit){
 val items=remember(qv,ctrl){ if(ctrl==null)emptyList() else List(ctrl.mediaItemCount){ctrl.getMediaItemAt(it)} }
 val cur=ctrl?.currentMediaItemIndex?:-1
 val tick=rememberTick()
 val ls=rememberLazyListState()
 LaunchedEffect(Unit){if(cur>0)ls.scrollToItem(cur)}
 Column(Modifier.fillMaxSize().statusBarsPadding()){
  Text("Antrean (${items.size})",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(horizontal=16.dp,vertical=12.dp))
  if(items.isEmpty())Text("Antrean kosong",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
  LazyColumn(Modifier.fillMaxWidth().weight(1f),state=ls,contentPadding=PaddingValues(bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+16.dp)){
   itemsIndexed(items,key={i,m->"$i:${m.mediaId}"},contentType={_,_->0}){i,m->
    val s=m.mediaId.toLongOrNull()?.let{byId[it]}
    val now=i==cur
    Row(Modifier.fillMaxWidth().clickable{tick();ctrl?.seekTo(i,0L);ctrl?.play()}.padding(start=12.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically){
     Cover(s,Modifier.size(40.dp),shape=RoundedCornerShape(8.dp),px=120)
     Spacer(Modifier.width(10.dp))
     Column(Modifier.weight(1f)){
      Text(s?.title?:m.mediaMetadata.title?.toString()?:"",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,
       color=if(now)MaterialTheme.colorScheme.primary else Color.Unspecified,fontWeight=if(now)FontWeight.Bold else FontWeight.Normal)
      Text(s?.artist?:m.mediaMetadata.artist?.toString()?:"",maxLines=1,overflow=TextOverflow.Ellipsis,
       style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     IconButton({tick();ctrl?.removeMediaItem(i);onChanged()},Modifier.size(36.dp)){Icon(Icons.Default.Close,"Hapus dari antrean",Modifier.size(18.dp))}
    }
   }
  }
 }
}

/**
 * Drawer antrean di sisi kanan (lebar diatur QUEUE_WIDTH_FRACTION). Geser ke kiri di mana saja untuk membuka;
 * geser ke kanan, ketuk area gelap, atau tombol kembali untuk menutup.
 */
/** Lebar drawer antrean sebagai pecahan lebar layar (0.5 = setengah layar, 0.75 = tiga perempat layar). */
const val QUEUE_WIDTH_FRACTION=0.75f

@Composable fun QueueDrawerHost(ctrl:MediaController?,byId:Map<Long,Song>,nowId:String?,qv:Int,onChanged:()->Unit,onOpenChanged:(Boolean)->Unit,content:@Composable ()->Unit){
 val scope=rememberCoroutineScope()
 var p by remember{mutableFloatStateOf(0f)}   // 0 = tertutup, 1 = terbuka
 var job by remember{mutableStateOf<Job?>(null)}
 // dibaca lewat derivedStateOf supaya komposisi hanya berubah di batas 0, bukan tiap frame geser
 val closed by remember{derivedStateOf{p==0f}}
 LaunchedEffect(closed){onOpenChanged(!closed)}
 BoxWithConstraints(Modifier.fillMaxSize()){
  val dw=maxWidth*QUEUE_WIDTH_FRACTION
  val dwPx=with(LocalDensity.current){dw.toPx()}
  fun go(t:Float){job?.cancel();job=scope.launch{animate(p,t,0f,tween(220)){v,_->p=v}}}
  val drag=rememberDraggableState{dx->job?.cancel();p=(p-dx/dwPx).coerceIn(0f,1f)}
  BackHandler(!closed){go(0f)}
  Box(Modifier.fillMaxSize().draggable(drag,Orientation.Horizontal,
   onDragStopped={vel->go(if(vel<-700f)1f else if(vel>700f)0f else if(p>0.5f)1f else 0f)})){
   content()
   if(closed)Box(Modifier.align(Alignment.CenterEnd).padding(end=3.dp).size(width=4.dp,height=40.dp).clip(CircleShape)
    .background(MaterialTheme.colorScheme.onSurface.copy(alpha=0.25f)))
   if(!closed){
    // alpha & posisi dibaca di fase layer/draw (bukan komposisi) -> tidak ada recomposition per frame
    Box(Modifier.fillMaxSize().graphicsLayer{alpha=p}.background(Color.Black.copy(alpha=0.45f)).pointerInput(Unit){detectTapGestures{go(0f)}})
    Surface(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(dw).graphicsLayer{translationX=(1f-p)*dwPx},
     shape=RoundedCornerShape(topStart=24.dp,bottomStart=24.dp),color=glassColor(MaterialTheme.colorScheme.surfaceContainerHigh,0.72f),
     border=if(LocalGlass.current)BorderStroke(1.dp,glassEdge) else null){
     QueuePanel(ctrl,byId,nowId,qv,onChanged)
    }
   }
  }
 }
}

/**
 * Pengaturan (popup): baris 1 = folder sumber musik (bisa lebih dari satu), baris 2 = tema,
 * baris terakhir = kredit aplikasi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsDialog(mode:Int,onMode:(Int)->Unit,ytOn:Boolean,onYt:(Boolean)->Unit,ytMax:Boolean,onYtMax:(Boolean)->Unit,glass:Boolean,onGlass:(Boolean)->Unit,folders:List<String>,onAddFolder:()->Unit,onRemoveFolder:(String)->Unit,onAllFolders:()->Unit,onDismiss:()->Unit){
 val c=LocalContext.current
 val tick=rememberTick()
 val appName=remember{c.applicationInfo.loadLabel(c.packageManager).toString()}
 val ver=remember{(runCatching{c.packageManager.getPackageInfo(c.packageName,0).versionName}.getOrNull()?:"1.0").trimStart('v','V')}
 val sub=MaterialTheme.colorScheme.onSurfaceVariant
 AlertDialog(containerColor=dlgColor(),onDismissRequest=onDismiss,title={Text("Pengaturan")},
  text={GlassDialogBlur();Column(Modifier.verticalScroll(rememberScrollState())){
   // 1. folder sumber musik
   Text("Folder sumber musik",style=MaterialTheme.typography.titleSmall)
   Text(if(folders.isEmpty())"Semua folder dipindai" else "Hanya folder berikut yang dipindai",style=MaterialTheme.typography.bodySmall,color=sub)
   Spacer(Modifier.height(8.dp))
   folders.forEach{f->
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Icon(Icons.Default.Folder,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
     Spacer(Modifier.width(8.dp))
     Text(Folders.label(f),Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
     IconButton({tick();onRemoveFolder(f)},Modifier.size(36.dp)){Icon(Icons.Default.Close,"Hapus folder",Modifier.size(18.dp))}
    }
   }
   Row(verticalAlignment=Alignment.CenterVertically){
    FilledTonalButton({tick();onAddFolder()}){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Tambah folder")}
    if(folders.isNotEmpty())TextButton({tick();onAllFolders()}){Text("Pindai semua")}
   }
   HorizontalDivider(Modifier.padding(vertical=16.dp))
   // 1b. streaming YouTube
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){
     Text("Streaming YouTube",style=MaterialTheme.typography.titleSmall)
     Text("Menambah daftar YouTube Trending di Home (tarik ke bawah untuk memuat ulang)",style=MaterialTheme.typography.bodySmall,color=sub)
    }
    Switch(ytOn,{tick();onYt(it)})
   }
   if(ytOn){
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){
      Text("Kualitas audio tertinggi",style=MaterialTheme.typography.titleSmall)
      Text(if(ytMax)"Memakai stream dengan bitrate terbesar yang tersedia (biasanya Opus). Lebih boros kuota." else "Standar: utamakan AAC/M4A",style=MaterialTheme.typography.bodySmall,color=sub)
     }
     Switch(ytMax,{tick();onYtMax(it)})
    }
   }
   if(ytOn)Text("Memakai ekstraktor tidak resmi: bisa berhenti bekerja sewaktu-waktu dan tidak sesuai syarat layanan YouTube.",
    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=6.dp))
   HorizontalDivider(Modifier.padding(vertical=16.dp))
   // 1c. efek glass
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){
     Text("Glass effect",style=MaterialTheme.typography.titleSmall)
     Text("Navbar, kartu, pemutar, dan dialog jadi tembus pandang seperti kaca. Blur latar sungguhan butuh Android 12+",style=MaterialTheme.typography.bodySmall,color=sub)
    }
    Switch(glass,{tick();onGlass(it)})
   }
   HorizontalDivider(Modifier.padding(vertical=16.dp))
   // 2. tema
   Text("Tema",style=MaterialTheme.typography.titleSmall)
   Spacer(Modifier.height(8.dp))
   val themes=listOf("Sistem","Terang","Gelap")
   var themeOpen by remember{mutableStateOf(false)}
   ExposedDropdownMenuBox(themeOpen,{themeOpen=it}){
    OutlinedTextField(themes.getOrElse(mode){themes[0]},{},Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),readOnly=true,singleLine=true,
     trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(themeOpen)})
    ExposedDropdownMenu(themeOpen,{themeOpen=false}){
     themes.forEachIndexed{i,t->
      DropdownMenuItem({Text(t)},{tick();onMode(i);themeOpen=false},
       trailingIcon={if(mode==i)Icon(Icons.Default.Check,null,Modifier.size(18.dp))})
     }
    }
   }
   HorizontalDivider(Modifier.padding(vertical=16.dp))
   // 3. kredit
   Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
    Text("$appName - v$ver",style=MaterialTheme.typography.labelLarge)
    Text("Dibuat dengan Claude",style=MaterialTheme.typography.bodySmall,color=sub)
   }
  }},
  confirmButton={TextButton({tick();onDismiss()}){Text("Tutup")}})
}

/** Sleep timer: berhenti setelah beberapa menit atau di akhir lagu. Timer berjalan di service. */
@Composable fun SleepDialog(endMs:Long,nowMs:Long,onPick:(Int)->Unit,onDismiss:()->Unit){
 val tick=rememberTick()
 val opts=listOf("15 menit" to 15,"30 menit" to 30,"45 menit" to 45,"60 menit" to 60,"Sampai lagu selesai" to -1)
 AlertDialog(containerColor=dlgColor(),onDismissRequest=onDismiss,title={Text("Sleep timer")},
  text={GlassDialogBlur();Column{
   if(endMs!=0L)Text(if(endMs<0)"Aktif: musik berhenti di akhir lagu ini" else "Aktif: musik berhenti dalam ${fmt((endMs-nowMs).coerceAtLeast(0L))}",
    color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(bottom=8.dp))
   opts.forEach{(t,m)->TextButton({tick();onPick(m)},Modifier.fillMaxWidth()){Text(t,Modifier.fillMaxWidth(),textAlign=TextAlign.Start)}}
  }},
  confirmButton={if(endMs!=0L)TextButton({tick();onPick(0)}){Text("Matikan timer")}},
  dismissButton={TextButton({tick();onDismiss()}){Text("Tutup")}})
}

/** Equalizer sistem yang menempel ke sesi audio player. Pengaturan tersimpan. */
@Composable fun EqDialog(onDismiss:()->Unit){
 val c=LocalContext.current
 val tick=rememberTick()
 val n=Eq.bands
 val rng=Eq.range
 var on by remember{mutableStateOf(Eq.isOn(c))}
 val lv=remember{mutableStateListOf<Float>().apply{for(b in 0 until n)add(Eq.level(c,b).toFloat())}}
 AlertDialog(containerColor=dlgColor(),onDismissRequest={Eq.save(c);onDismiss()},title={Text("Equalizer")},
  text={
   GlassDialogBlur()
   if(n==0||rng==null)Text("Equalizer belum tersedia. Putar lagu terlebih dahulu.")
   else Column(Modifier.verticalScroll(rememberScrollState())){
    Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically){
     Text("Aktifkan");Switch(on,{tick();on=it;Eq.setOn(c,it)})
    }
    for(b in 0 until n){
     Text("${Eq.label(b)}   ${"%+.1f".format(lv[b]/100f)} dB",style=MaterialTheme.typography.bodySmall)
     Slider(lv[b],{lv[b]=it;Eq.setLevel(c,b,it.toInt())},valueRange=rng[0].toFloat()..rng[1].toFloat(),enabled=on,onValueChangeFinished={Eq.save(c)})
    }
   }
  },
  confirmButton={TextButton({tick();Eq.save(c);onDismiss()}){Text("Selesai")}},
  dismissButton={if(n>0)TextButton({tick();for(b in 0 until n){lv[b]=0f;Eq.setLevel(c,b,0)};Eq.save(c)}){Text("Reset")}})
}