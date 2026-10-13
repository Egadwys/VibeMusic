<div align="center">

# 🎵 VibeMusic

**Pemutar musik lokal untuk Android: minimalis, ringan, dengan lirik tersinkron dan visualizer gelombang.**

![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer-FF6F00)
![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)

</div>

---

## ✨ Fitur

### Perpustakaan
| | |
|---|---|
| 🏠 **Home per folder** | Menampilkan subfolder di dalam folder `Music`; ketuk folder untuk melihat lagunya |
| 💿 **Album, Artis, Playlist** | Telusuri musik dengan cara lain; semua daftar berbentuk **grid 3 kolom** |
| 🖼️ **Cover acak** | Tiap folder/album/artis menampilkan cover salah satu lagunya, berganti setiap halaman dibuka |
| 📑 **Playlist** | Buat, hapus, dan isi playlist lewat menu ⋮ pada lagu; tersimpan di perangkat |
| 👆 **Navigasi** | Navbar mengambang, geser kiri/kanan antar tab, tombol play/pause bercover dengan cincin progres |

### Pemutar
| | |
|---|---|
| 📝 **Lirik tersinkron** | Satu baris lirik per waktu dari [LRCLIB](https://lrclib.net), berganti dengan efek fade |
| 🌊 **Visualizer** | Gelombang berdenyut di sekeliling cover; memudar dan berhenti total saat pause |
| 🔀 **Acak & ulangi** | Acak, ulangi semua, ulangi satu lagu |
| 📱 **Sheet pemutar** | Geser naik untuk membuka, geser turun untuk menutup, mengikuti jari |
| ☀️ **Stay awake** | Tekan cover untuk menahan layar tetap menyala selama musik diputar; muncul notifikasi *"Stay awake for music"* |
| 🔁 **Lanjutkan** | Membuka kembali lagu dan posisi terakhir yang diputar |

### Tampilan & rasa
| | |
|---|---|
| 🎨 **Material 3 minimalis** | Material You (warna dinamis) di Android 12+, skema teal di bawahnya |
| 🌗 **Terang / Gelap AMOLED** | Ikuti sistem → terang → hitam murni (`#000000`); ikon status bar menyesuaikan |
| 📳 **Getaran lembut** | Umpan balik halus saat menekan navbar, tombol, kartu, dan kontrol pemutar |
| 🧩 **Widget** | Widget layar utama 4×1 dengan judul, artis, prev, play/pause, next |
| 🔔 **Latar belakang** | Tetap memutar saat layar mati, dengan kontrol di notifikasi |

### Metadata & cover
- Judul, artis, dan album dibaca dari **file** lebih dulu.
- Hanya field yang masih kosong disinkronkan dari internet (iTunes Search API), satu lagu tiap 1,5 detik di latar belakang, lalu disimpan di cache.
- Cover hanya diambil dari file (tag tertanam). Kalau tidak ada, tampil logo **piringan hitam**.

## 🖼️ Tampilan

> Tambahkan tangkapan layar di folder `docs/` lalu tautkan di sini, misalnya `![Home](docs/home.png)`.

```
docs/
├── home.png
├── player.png
└── widget.png
```

## 🚀 Cara Build

### Opsi 1: GitHub Actions (tanpa install apa pun)

1. Buka tab **Actions** → workflow **Build APK** → **Run workflow** (workflow ini dijalankan manual).
2. Tunggu selesai (sekitar 4 sampai 8 menit untuk build pertama).
3. Unduh artifact **`Musik-debug-apk`** di bagian *Artifacts*. Isinya APK **release** (R8 aktif, jauh lebih mulus dari build debug) yang ditandatangani dengan debug key.
4. Ekstrak, salin APK ke HP, lalu install (izinkan sumber tidak dikenal).

> ⚠️ Debug key dibuat baru di setiap build GitHub Actions, jadi Android akan menolak update di atas versi sebelumnya. **Uninstall dulu** versi lama sebelum menginstal (playlist ikut terhapus). Untuk update tanpa uninstall, gunakan keystore tetap lewat GitHub Secrets.

### Opsi 2: Android Studio

1. Buka folder proyek di Android Studio (Koala atau lebih baru).
2. Tunggu Gradle sync selesai.
3. Sambungkan perangkat atau emulator (Android 11+), lalu klik **Run ▶**.

## 🧱 Arsitektur

```
app/src/main/
├── java/com/ega/project/vibemusic/
│   ├── MainActivity.kt   # Tema, UI Compose, navigasi, sheet pemutar, visualizer
│   ├── Service.kt        # PlaybackService (Media3) + PlayerWidget
│   ├── Data.kt           # Song, MediaStore, Playlists, cover, metadata, klien LRCLIB
│   └── StayAwake.kt      # Notifikasi "Stay awake for music"
└── res/
    ├── layout/widget_player.xml
    ├── xml/widget_info.xml
    ├── drawable/ic_vinyl.xml        # logo piringan hitam
    └── mipmap-anydpi/ic_launcher.xml
```

| Lapisan | Teknologi |
|---|---|
| UI | Jetpack Compose, Material 3, Material You |
| Pemutaran | Media3 ExoPlayer + `MediaSessionService` |
| Data lagu | `MediaStore.Audio` (termasuk `RELATIVE_PATH` untuk folder) |
| Cover | `ContentResolver.loadThumbnail` + LruCache |
| Playlist & state | `SharedPreferences` (JSON) |
| Lirik | LRCLIB REST API (format LRC) |
| Widget | `AppWidgetProvider` + `RemoteViews` |
| Build | Gradle (Kotlin DSL), R8, GitHub Actions |

## 🔐 Izin & Privasi

| Izin | Kegunaan |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Membaca file musik |
| `INTERNET` | Mengambil lirik dan melengkapi metadata |
| `POST_NOTIFICATIONS` | Notifikasi pemutar dan notifikasi *stay awake* |
| `FOREGROUND_SERVICE` / `..._MEDIA_PLAYBACK` | Pemutaran di latar belakang |

Koneksi internet hanya dipakai untuk dua hal: mencari lirik ke LRCLIB (mengirim judul, artis, album, durasi) dan melengkapi metadata yang kosong ke iTunes Search (mengirim judul dan artis). File musik Anda tidak pernah diunggah.

## ℹ️ Catatan

- Minimal **Android 11 (API 30)**, karena info genre dan `RELATIVE_PATH` bergantung pada versi itu.
- Lirik hanya tampil jika LRCLIB punya data yang cocok dengan lagu.
- Visualizer adalah animasi dekoratif, bukan analisis audio asli.
- Pencocokan metadata online berdasarkan judul, jadi lagu dengan judul umum bisa salah terisi. Data dari file tidak pernah ditimpa.
- Layar hanya bisa ditahan menyala saat aplikasi terlihat di depan, dan hanya selama musik diputar.

## 🗺️ Rencana

- [x] Cover album
- [x] Acak dan ulangi
- [x] Lanjutkan posisi terakhir
- [x] Material You dan getaran lembut
- [x] Stay awake
- [ ] Cache lirik offline
- [ ] Pencarian lagu
- [ ] Antrean pemutaran
- [ ] Sleep timer
- [ ] Equalizer
- [ ] Visualizer dari audio asli

## 🙏 Kredit

- [LRCLIB](https://lrclib.net) untuk API lirik tersinkron yang gratis dan terbuka
- [AndroidX Media3](https://developer.android.com/media/media3) dan [Jetpack Compose](https://developer.android.com/jetpack/compose)

---

<div align="center">Dibuat dengan ❤️ dan Kotlin</div>
