<p align="center">
  <img src="docs/logo.svg" width="120" alt="Logo VibeMusic">
</p>
<h1 align="center">VibeMusic</h1>

Pemutar musik offline untuk Android, dengan lirik tersinkron dan visualizer.

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)

## Fitur

- Pilih folder sumber musik sendiri
- Telusuri per folder, album, artis, playlist, dan pencarian
- Lirik tersinkron dari [LRCLIB](https://lrclib.net)
- Dua visualizer: gelombang mengikuti audio dan animasi gelombang berlapis
- Antrean, acak, ulangi, dan lanjut dari posisi terakhir
- Sleep timer, equalizer, dan fade saat play/pause/pindah lagu
- Widget layar utama dan kontrol di notifikasi
- Material You, tema sistem / terang / gelap AMOLED
- Opsional: streaming YouTube (daftar Trending 50 lagu di Home, tarik ke bawah untuk memuat ulang). Bila lagu gagal dimuat: diulang sekali, lalu lompat ke lagu berikutnya, lalu muncul popup masalah jaringan
- Opsional: Glass effect di Pengaturan (navbar, kartu, pemutar, dan dialog tembus pandang; blur latar penuh di Android 12+)

## Build

Butuh JDK 17 dan Gradle 8.9.

```bash
gradle assembleRelease
```

APK ada di `app/build/outputs/apk/release/`. Bisa juga lewat Android Studio, atau workflow **Release APK** di GitHub Actions.

> APK ditandatangani dengan debug key, jadi update dari build lain perlu uninstall dulu.

## Izin

| Izin | Fungsi |
|---|---|
| `READ_MEDIA_AUDIO` | Membaca file musik |
| `INTERNET` | Lirik dan metadata |
| `POST_NOTIFICATIONS` | Notifikasi pemutar |

Internet hanya dipakai untuk mencari lirik (LRCLIB) dan melengkapi metadata kosong (iTunes Search). File musik tidak diunggah.