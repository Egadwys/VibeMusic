package com.ega.project.vibemusic
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Status halaman YouTube; hidup di App supaya hasil tidak hilang saat pindah tab. */
class YtState(private val scope:CoroutineScope){
 var query by mutableStateOf("")
 var submitted by mutableStateOf("")            // kueri yang hasilnya sedang ditampilkan
 var mode by mutableIntStateOf(0)               // 0 = lagu, 1 = playlist
 var songs by mutableStateOf<List<Song>>(emptyList())
 var lists by mutableStateOf<List<YtPl>>(emptyList())
 var busy by mutableStateOf(false)
 var error by mutableStateOf<String?>(null)
 val sections=mutableStateMapOf<String,List<Song>>()
 var secBusy by mutableStateOf(false)
 var openPl by mutableStateOf<YtPl?>(null)
 var plSongs by mutableStateOf<List<Song>>(emptyList())
 var plBusy by mutableStateOf(false)

 fun clearSearch(){query="";submitted="";songs=emptyList();lists=emptyList();error=null}

 fun search(){
  val q=query.trim()
  if(q.isEmpty()){clearSearch();return}
  // tempel link playlist YouTube -> langsung dibuka
  if(q.startsWith("http")&&"list=" in q){openPlaylist(YtPl("Playlist","",q,null));return}
  openPl=null;submitted=q;busy=true;error=null;songs=emptyList();lists=emptyList()
  scope.launch{
   try{
    coroutineScope{
     val s=async{runCatching{Yt.songs(q)}}
     val p=async{runCatching{Yt.playlists(q)}}
     val rs=s.await();val rp=p.await()
     songs=rs.getOrDefault(emptyList());lists=rp.getOrDefault(emptyList())
     if(rs.isFailure&&rp.isFailure)error=rs.exceptionOrNull()?.message?:"Gagal memuat"
    }
   }finally{busy=false}
  }
 }

 fun openPlaylist(p:YtPl){
  openPl=p;plSongs=emptyList();plBusy=true;error=null
  scope.launch{
   try{plSongs=Yt.playlistSongs(p.url)}
   catch(e:Exception){error=e.message?:"Gagal memuat playlist"}
   finally{plBusy=false}
  }
 }

 fun loadSections(force:Boolean=false){
  if(secBusy||(!force&&sections.isNotEmpty()))return
  secBusy=true;error=null
  scope.launch{
   try{
    val res=coroutineScope{Yt.SECTIONS.map{sec->async{sec.title to runCatching{Yt.section(sec)}}}.map{it.await()}}.toMap()
    var failed:String?=null
    for(sec in Yt.SECTIONS){
     val r=res[sec.title]?:continue
     r.onSuccess{l->
      // buang lagu yang sudah tampil di kategori yang dikecualikan (mis. Internasional tanpa lagu Top Indonesia)
      val ex=sec.exclude?.let{res[it]?.getOrNull()}?.map{s->s.id}?.toSet().orEmpty()
      sections[sec.title]=if(ex.isEmpty())l else l.filter{s->s.id !in ex}
     }
     if(r.isFailure&&failed==null)failed=r.exceptionOrNull()?.message
    }
    if(sections.isEmpty())error=failed
   }finally{secBusy=false}
  }
 }

 /** buka daftar lengkap satu kategori memakai tampilan detail yang sama dengan playlist */
 fun openSection(sec:YtSection,l:List<Song>){openPl=YtPl(sec.title,"","",null);plSongs=l;plBusy=false;error=null}
}

/** Halaman YouTube: pencarian, kategori (Top Indonesia / Top Internasional / Trending), dan playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun YtTab(st:YtState,nowId:String?,pl:Playlists,onPlay:(List<Song>,Int)->Unit,onNext:(Song)->Unit,onAdd:(Song)->Unit){
 val tick=rememberTick()
 val focus=LocalFocusManager.current
 val sub=MaterialTheme.colorScheme.onSurfaceVariant
 LaunchedEffect(Unit){st.loadSections()}
 BackHandler(st.openPl!=null||st.submitted.isNotEmpty()){
  if(st.openPl!=null)st.openPl=null else st.clearSearch()
 }
 Column(Modifier.fillMaxSize()){
  OutlinedTextField(st.query,{st.query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),singleLine=true,
   shape=RoundedCornerShape(28.dp),placeholder={Text("Cari lagu, artis, playlist...")},
   leadingIcon={Icon(Icons.Default.Search,null)},
   trailingIcon={if(st.query.isNotEmpty())IconButton({tick();st.clearSearch();st.openPl=null}){Icon(Icons.Default.Close,"Hapus")}},
   keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
   keyboardActions=KeyboardActions(onSearch={focus.clearFocus();st.search()}))

  val open=st.openPl
  when{
   // ---- isi satu playlist
   open!=null->{
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
     IconButton({tick();st.openPl=null}){Icon(Icons.AutoMirrored.Filled.ArrowBack,null)}
     Column(Modifier.weight(1f)){
      Text(open.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
      if(open.by.isNotBlank())Text(open.by,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=sub)
     }
     if(st.plSongs.isNotEmpty())FilledTonalButton({tick();onPlay(st.plSongs,0)}){
      Icon(Icons.Default.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("Putar semua")
     }
    }
    when{
     st.plBusy->Box(Modifier.fillMaxSize(),Alignment.Center){CircularProgressIndicator()}
     st.plSongs.isEmpty()->Msg(st.error?:"Playlist kosong")
     else->SongGrid(st.plSongs,nowId,pl,null,onNext,onAdd){onPlay(listOf(st.plSongs[it]),0)}   // satu lagu saja, tanpa mengisi antrean
    }
   }
   // ---- hasil pencarian
   st.submitted.isNotEmpty()->{
    Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     FilterChip(st.mode==0,{tick();st.mode=0},label={Text("Lagu")})
     FilterChip(st.mode==1,{tick();st.mode=1},label={Text("Playlist")})
    }
    when{
     st.busy->Box(Modifier.fillMaxSize(),Alignment.Center){CircularProgressIndicator()}
     st.error!=null->Msg(st.error!!)
     st.mode==0->if(st.songs.isEmpty())Msg("Tidak ada lagu untuk \"${st.submitted}\"") else SongGrid(st.songs,nowId,pl,null,onNext,onAdd){onPlay(listOf(st.songs[it]),0)}
     else->if(st.lists.isEmpty())Msg("Tidak ada playlist untuk \"${st.submitted}\"") else
      Grid(st.lists,{it.url}){_,p->Tile(p.asSong(),p.title,p.by){st.openPlaylist(p)}}
    }
   }
   // ---- beranda: kategori
   else->{
    val any=st.sections.isNotEmpty()
    if(!any&&st.secBusy)Box(Modifier.fillMaxSize(),Alignment.Center){CircularProgressIndicator()}
    else if(!any)Column(Modifier.fillMaxSize(),Arrangement.Center,Alignment.CenterHorizontally){
     Text(st.error?:"Belum ada data",color=sub,modifier=Modifier.padding(24.dp))
     FilledTonalButton({tick();st.loadSections(true)}){Text("Coba lagi")}
    }
    else PullToRefreshBox(isRefreshing=st.secBusy,onRefresh={st.loadSections(true)},modifier=Modifier.fillMaxSize()){
     LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+96.dp)){
      for(sec in Yt.SECTIONS){
       val l=st.sections[sec.title]?:continue
       if(l.isEmpty())continue
       item(key=sec.title){
        Column{
         Text(sec.title,Modifier.padding(start=16.dp,end=16.dp,top=16.dp,bottom=4.dp),style=MaterialTheme.typography.titleMedium)
         // 5 lagu + 1 tombol "Lihat semua"
         LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
          itemsIndexed(l.take(5),key={_,s->s.id}){_,s->
           Column(Modifier.width(132.dp).clickable{tick();onPlay(listOf(s),0)}.padding(4.dp)){
            Cover(s,Modifier.fillMaxWidth())
            Text(s.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge,
             color=if(s.id.toString()==nowId)MaterialTheme.colorScheme.primary else Color.Unspecified,modifier=Modifier.padding(top=6.dp))
            Text(s.artist,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=sub)
           }
          }
          item(key="all"){
           Column(Modifier.width(132.dp).clickable{tick();st.openSection(sec,l)}.padding(4.dp)){
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),Alignment.Center){
             Icon(Icons.AutoMirrored.Filled.ArrowForward,null)
            }
            Text("Lihat semua",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=6.dp))
           }
          }
         }
        }
       }
      }
     }
    }
   }
  }
 }
}

@Composable private fun Msg(t:String){
 Box(Modifier.fillMaxSize(),Alignment.Center){Text(t,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(24.dp))}
}