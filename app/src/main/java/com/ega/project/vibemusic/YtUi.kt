package com.ega.project.vibemusic
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Status daftar YouTube di Home (satu daftar: Trending, [YT_TRENDING_SIZE] lagu).
 * Hidup di App supaya hasil tidak hilang saat pindah tab. Tarik ke bawah di Home = [loadTrending] dengan force=true.
 */
class YtState(private val scope:CoroutineScope){
 var trending by mutableStateOf<List<Song>>(emptyList())
 var busy by mutableStateOf(false)

 fun loadTrending(force:Boolean=false){
  if(busy||(!force&&trending.isNotEmpty()))return
  busy=true
  scope.launch{
   try{trending=Yt.section(Yt.TRENDING)}
   catch(e:CancellationException){throw e}
   catch(e:Exception){}   // gagal: daftar lama tetap dipakai; tile di Home menawarkan coba lagi bila masih kosong
   finally{busy=false}
  }
 }
}