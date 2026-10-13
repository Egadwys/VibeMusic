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
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 val sp=LocalContext.current.getSharedPreferences("s",0)
 var mode by remember{mutableIntStateOf(sp.getInt("mode",0))}
 MusikTheme(mode){Surface(Modifier.fillMaxSize()){Perm{App(mode){mode=(mode+1)%3;sp.edit().putInt("mode",mode).apply()}}}}
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
     val r = inner + rMax * (0.10f + 0.90f * e * n * n) // n^2 -> puncak lebih runcing
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
 Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick={tick();onClick()}).padding(4.dp)){
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

@Composable fun SongGrid(list:List<Song>,nowId:String?,pl:Playlists,from:String?,onPlay:(Int)->Unit){
 val names=pl.map.keys.sorted()
 Grid(list,{it.id}){i,s->
  val menu:List<Pair<String,()->Unit>> = names.map{n->"Tambah ke $n" to {pl.add(n,s.id)}}+
   (if(from!=null)listOf("Hapus dari playlist" to {pl.remove(from,s.id)}) else emptyList())
  Tile(s,s.title,s.artist,menu,s.id.toString()==nowId){onPlay(i)}
 }
}

@Composable fun Groups(g:Map<String,List<Song>>,open:(String)->Unit){
 val keys=remember(g){g.keys.sorted()}
 val pick=remember(g){g.mapValues{it.value.random()}}
 Grid(keys,{it}){_,k->Tile(pick[k],k,"${g.getValue(k).size} lagu"){open(k)}}
}

@Composable fun HomeTab(g:Map<String,List<Song>>,open:(String)->Unit){
 if(g.isEmpty())Box(Modifier.fillMaxSize(),Alignment.Center){Text("Tidak ada lagu di folder ini")} else Groups(g,open)
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
 if(dlg)AlertDialog(onDismissRequest={dlg=false},confirmButton={TextButton({pl.create(name.trim());name="";dlg=false}){Text("Buat")}},
  title={Text("Playlist baru")},text={OutlinedTextField(name,{name=it},singleLine=true,label={Text("Nama")})})
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun App(mode:Int,onTheme:()->Unit){
 val c=LocalContext.current
 val tick=rememberTick()
 val appSp = c.getSharedPreferences("AppState", Context.MODE_PRIVATE) 
 
 LaunchedEffect(Unit){ appSp.edit().remove("selected_folder_uri").apply() }
 var songs by remember{mutableStateOf(loadSongs(c))}

 val pl=remember{Playlists(c)}
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
 var tab by remember{mutableIntStateOf(appSp.getInt("tab", 0))}
 var detail by remember{mutableStateOf<String?>(appSp.getString("detail", null))}
 val pagerState = rememberPagerState(initialPage = tab) { 4 }

 LaunchedEffect(tab, detail) { appSp.edit().putInt("tab", tab).putString("detail", detail).apply() }
 val scope = rememberCoroutineScope()
 var navTarget by remember{mutableStateOf<Int?>(null)}
 var navJob by remember{mutableStateOf<Job?>(null)}
 // swipe -> update tab (diabaikan selama perpindahan dari tap navbar sedang berjalan)
 LaunchedEffect(pagerState) { snapshotFlow{pagerState.settledPage}.collect{ if(navTarget==null){ if(tab!=it) tick(); tab = it } } }
 fun selectTab(i:Int){
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
   appSp.edit().putString("last_id",id).putLong("last_pos",p.currentPosition).apply()
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

 LaunchedEffect(Unit){
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
  nowId=p.currentMediaItem?.mediaId;playing=p.isPlaying;shuffle=p.shuffleModeEnabled;repeat=p.repeatMode
  val l=object:Player.Listener{override fun onEvents(q:Player,e:Player.Events){
   nowId=q.currentMediaItem?.mediaId;playing=q.isPlaying;shuffle=q.shuffleModeEnabled;repeat=q.repeatMode}}
  p.addListener(l);onDispose{p.removeListener(l)}
 }
 
 LaunchedEffect(ctrl){while(true){pos.value=ctrl?.currentPosition?:0L;delay(250)}}

 val byId=remember(songs){songs.associateBy{it.id}}
 
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
    val idx = queue.indexOfFirst { it.id == lastSong.id }
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
 BackHandler(expanded||detail!=null){if(expanded)animateSheet(0f) else detail=null}
 fun play(l:List<Song>,i:Int){
  // simpan antrean agar bisa dipulihkan setelah aplikasi ditutup
  appSp.edit().putString("queue",l.joinToString(","){it.id.toString()}).apply()
  ctrl?.run{setMediaItems(l.map{it.item()},i,0L);prepare();play()}
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
  Scaffold(topBar={
   TopAppBar(
    title={AnimatedContent(targetState=title,label="title"){t->Text(t,maxLines=1,overflow=TextOverflow.Ellipsis)}},
    navigationIcon={if(detail!=null)IconButton({tick();detail=null}){Icon(Icons.AutoMirrored.Filled.ArrowBack,null)}},
    actions={
     IconButton({tick();onTheme()}){Icon(when(mode){0->Icons.Default.SettingsBrightness;1->Icons.Default.LightMode;else->Icons.Default.DarkMode},null)}
    }
   )
  },
  containerColor=Color.Transparent,
  bottomBar={FloatingNav(tab,cur,playing,pos,ctrl,dragMod){selectTab(it)}}){pad->
  
  if(detail!=null){
   val d = detail!!
   val n=d.drop(2)
   val ids=if(d[0]=='p')pl.map[n] else null
   val list=remember(d,songs,ids){when(d[0]){
    'f'->songs.filter{it.folder==n};'a'->songs.filter{it.album==n};'r'->songs.filter{it.artist==n}
    else->ids.orEmpty().mapNotNull{byId[it]}}}
   Box(Modifier.padding(top=pad.calculateTopPadding()).fillMaxSize().pointerInput(Unit){ 
    detectHorizontalDragGestures { _, dragAmount -> if (dragAmount > 40) detail = null }
   }) {
    SongGrid(list,nowId,pl,if(d[0]=='p')n else null){play(list,it)}
   }
  } else {
   HorizontalPager(state = pagerState, modifier = Modifier.padding(top=pad.calculateTopPadding()).fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
    when(page){
     0->HomeTab(byFolder){detail="f:$it"}
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
   Now(cur,ctrl,playing,pos,shuffle,repeat,sheetVisible,dragMod,stayAwake,{
    val turnOn=!stayAwake
    stayAwake=turnOn
    // pop-up cara pakai, hanya sekali: saat stay awake pertama kali diaktifkan
    if(turnOn&&!appSp.getBoolean("stay_awake_hint_shown",false)){
     appSp.edit().putBoolean("stay_awake_hint_shown",true).apply()
     showStayAwakeHint=true
    }
   }){ animateSheet(0f) }
  }
 }

 if(showStayAwakeHint)AlertDialog(
  onDismissRequest={showStayAwakeHint=false},
  title={Text("Stay awake aktif")},
  text={Text("Layar akan tetap menyala selama musik diputar. Tekan cover di pemutar kapan saja untuk menyalakan atau mematikan fitur ini.")},
  confirmButton={TextButton({tick();showStayAwakeHint=false}){Text("Mengerti")}}
 )
 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun FloatingNav(tab:Int,cur:Song?,playing:Boolean,pos:State<Long>,ctrl:MediaController?,dragMod:Modifier,onSelect:(Int)->Unit){
 val items=listOf("Home" to Icons.Default.Home,"Album" to Icons.Default.Album,"Artis" to Icons.Default.Person,"Playlist" to Icons.AutoMirrored.Filled.QueueMusic)
 val tick=rememberTick()
 Box(dragMod.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp,vertical=12.dp),contentAlignment=Alignment.Center){
  Surface(shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surfaceContainerHigh,tonalElevation=0.dp,shadowElevation=10.dp){
   Row(Modifier.animateContentSize().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){
    items.forEachIndexed{i,(t,ic)->
     val sel=tab==i
     Box(Modifier.width(56.dp).height(48.dp).clip(CircleShape)
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

@Composable fun Now(s:Song,ctrl:MediaController?,playing:Boolean,pos:State<Long>,shuffle:Boolean,repeat:Int,visible:Boolean,dragMod:Modifier,stayAwake:Boolean,onStayAwake:()->Unit,close:()->Unit){
 var lines by remember(s.id){mutableStateOf<List<Line>?>(null)}
 LaunchedEffect(s.id){lines=Lyrics.get(s)}
 val idx by remember(s.id){derivedStateOf{lines?.indexOfLast{it.ms<=pos.value}?:-1}}
 val on=MaterialTheme.colorScheme.primary;val off=MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f)
 
 val tick=rememberTick()
 Surface(Modifier.fillMaxSize().then(dragMod),shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)){Box(Modifier.fillMaxSize()){
  Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal=24.dp)){
   
   Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
    VisualizerWave(playing,visible)
    // tekan cover = aktif/nonaktifkan stay awake (cincin di sekeliling cover menandakan aktif)
    Box(Modifier.size(280.dp).clip(CircleShape).clickable{tick();onStayAwake()}
     .then(if(stayAwake)Modifier.border(3.dp,on,CircleShape) else Modifier)){
     Cover(s, Modifier.fillMaxSize(), shape = CircleShape, px = 700, zoom = 1.12f)
    }
   }

   Box(Modifier.fillMaxWidth().height(100.dp), Alignment.CenterStart){
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
  IconButton({tick();close()},Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)){Icon(Icons.Default.KeyboardArrowDown,null)}
 }}
}
