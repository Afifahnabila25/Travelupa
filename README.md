# 🌍 Travelupa - Aplikasi Rekomendasi Wisata

**Travelupa** adalah aplikasi Android berbasis **Jetpack Compose** yang dirancang untuk memberikan rekomendasi tempat wisata di Jawa Timur. Aplikasi ini memungkinkan pengguna untuk melihat daftar wisata, menambahkan tempat baru beserta foto (dari Kamera atau Galeri), dan menyimpan data secara sinkron antara penyimpanan lokal (Room) dan Cloud (Firebase).

## ✨ Fitur Utama

Aplikasi ini mencakup berbagai implementasi teknologi Android modern:

* **Autentikasi Pengguna:** Login dan Register menggunakan **Firebase Authentication**.
* **Persistent Login:** Menggunakan **Kotlin Coroutines** untuk mengecek status login pengguna (User tidak perlu login ulang saat aplikasi dibuka kembali).
* **Cloud Database:** Menyimpan data nama dan deskripsi wisata secara *real-time* menggunakan **Firebase Firestore**.
* **Local Database (Offline):** Menyimpan path gambar secara lokal menggunakan **Room Database** agar hemat kuota dan cepat.
* **Kamera & Galeri:** Integrasi **CameraX** dan **File Picker** untuk mengambil atau memilih foto tempat wisata.
* **Navigasi:** Menggunakan **Jetpack Navigation** dengan implementasi *Navigation Drawer* (Menu Samping) dan proteksi rute (Logout membersihkan backstack).
* **UI Modern:** Dibangun sepenuhnya menggunakan **Jetpack Compose (Material 3)**.

## 🛠️ Teknologi yang Digunakan

* **Bahasa:** Kotlin
* **UI Toolkit:** Jetpack Compose (Material 3)
* **Backend:** Firebase (Auth & Firestore)
* **Local DB:** Room Database (SQLite)
* **Asynchronous:** Kotlin Coroutines & Flow
* **Image Loading:** Coil
* **Camera:** CameraX
* **Navigation:** Navigation Compose
* **Architecture:** MVVM (Model-View-ViewModel) concept

## 🚀 Cara Menjalankan Project

1.  **Clone Repository ini:**
    ```bash
    git clone [https://github.com/username-kamu/Travelupa.git](https://github.com/username-kamu/Travelupa.git)
    ```
2.  **Buka di Android Studio:**
    Buka Android Studio, pilih `Open` dan arahkan ke folder `Travelupa`.
3.  **Konfigurasi Firebase:**
    * Buat project baru di [Firebase Console](https://console.firebase.google.com/).
    * Aktifkan **Authentication** (Email/Password) dan **Firestore Database**.
    * Download file `google-services.json` dari Firebase Console.
    * Letakkan file `google-services.json` di dalam folder `app/` di project Android Studio.
4.  **Sync Gradle:**
    Tunggu hingga proses sinkronisasi selesai.
5.  **Run:**
    Jalankan aplikasi di Emulator atau Perangkat Fisik.

## 📂 Struktur Project

* `MainActivity.kt`: Entry point aplikasi dan setup Navigasi.
* `ImageEntity.kt`: Model data untuk tabel database Room.
* `ImageDao.kt`: Interface untuk akses database lokal.
* `AppDatabase.kt`: Konfigurasi Room Database.
* `Screen`: Sealed class untuk manajemen rute navigasi.

## 👤 Author

Dibuat oleh **Afifah Nabila Devi**
Mahasiswa Pengembangan Aplikasi Perangkat Bergerak (PAPB) 2025 Filkom UB.

---
*Happy Coding! 🚀*
