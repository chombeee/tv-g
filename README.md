# Glass TV

**Launcher kaca (frosted glass) untuk Android TV, Google TV dan TV Box. Skrin Home yang ringan, cantik dan bebas iklan, lengkap dengan Now Playing, lirik, waktu solat, cuaca dan wallpaper dari telefon.**

[![Muat turun APK](https://img.shields.io/badge/Muat_turun-APK_terkini-success?style=for-the-badge&logo=android)](https://github.com/chombeee/tv-g/releases/latest)

> 🚨 **Penting: jangan sideload APK mentah-mentah dan berhenti di situ.**
> Kalau cuma pasang APK guna pendrive atau app *Downloader*, ramai TV akan anggap Glass TV sebagai app biasa. Bila tekan butang **HOME**, TV tetap balik ke skrin utama asal yang penuh iklan.
> Lepas pasang, **jadikan Glass TV sebagai Home** guna langkah di [bahagian 2](#2-jadikan-glass-tv-sebagai-skrin-home).

- **Package:** `com.faris.glasstv`
- **Activity Home:** `com.gathin.tvhome.MainActivity`
- **Minimum:** Android 8 (API 26) ke atas
- **Layout:** Classic, Hero, Pill dan Material

---

## Apa yang anda dapat

- **Empat layout Home.** Classic, Hero, Pill dan Material. Tukar dalam *Preferences → Appearance → Home Layout*.
- **Kaca yang bergerak lembut.** Dock, butang atas dan menu menunjukkan wallpaper di belakangnya dengan kesan kaca. Fokus meluncur dari satu item ke item lain, tidak melompat.
- **Widget Now Playing.** Paparkan lagu yang sedang dimainkan (Spotify, YouTube Music dan lain-lain, ikut media session yang aktif) dengan art album, tajuk, artis, bar progress, masa berjalan dan jumlah masa.
  - Tekan **OK** pada kad untuk membuka app yang sedang memainkan lagu.
  - Tahan **OK** atau tekan **Menu** untuk Simpan / Like lagu (jika app menyokongnya).
  - Bar progress ada dua gaya dalam *Preferences → Widgets → Now Playing Bar*: **Kepala bercahaya** (lalai) atau **Api**.
- **Widget Lirik.** Lirik berjalan ikut masa lagu. Tekan **OK** untuk lirik skrin penuh dengan latar warna yang mengalir ikut art album. **Kiri/Kanan** tukar lagu, **OK** main/jeda, **Back** atau **Home** menutup.
- **Waktu solat.** Jadual enam waktu dengan waktu seterusnya diserlahkan.
- **Cuaca dan kualiti udara.** Skrin cuaca penuh dengan animasi ikut keadaan (matahari, bulan, awan, hujan, kilat, kabus). Lokasi boleh dikesan automatik dari IP TV atau dipilih sendiri.
- **Recent Apps.** Tekan **HOME** dua kali dari mana-mana untuk melihat app yang baru dibuka.
- **Susun app dari remote.** Tahan **OK** pada app untuk menu: Move, App info dan Sorok.
- **Skrin StandBy / screensaver.** Jam besar, kad solat, cuaca dan Now Playing bila TV terbiar.
- **Wallpaper dari telefon.** Imbas kod QR di TV, hantar gambar melalui Wi-Fi yang sama. Tiada app awan pihak ketiga.
- **Kemas kini OTA.** Semak dan pasang versi baru terus pada TV tanpa PC.
- **Pembersih cache.** Membersihkan cache app tanpa menyentuh log masuk atau data app.
- **Warna dinamik.** Warna Material mengikut wallpaper atau art album.

---

## 1. Pemasangan kali pertama

### 1.1 Hidupkan ADB pada TV
1. **Settings → Device Preferences → About**, tekan **Build** 7 kali sehingga Developer options dibuka.
2. **Developer options**, hidupkan **USB debugging** (atau **Network / Wireless debugging**).
3. Catat alamat IP TV di **Settings → Network & Internet**.

### 1.2 Sambung dari PC (Windows, macOS atau Linux)
Pasang [Android platform-tools](https://developer.android.com/tools/releases/platform-tools), kemudian:

```bash
adb connect 192.168.x.x:5555      # ganti dengan IP TV
adb devices                       # mesti keluar "device" (terima prompt di TV: Always allow)
```

Google TV atau Android 11 ke atas dengan **Wireless debugging**: guna `adb pair IP:PORT_PAIR` dahulu (kod pairing dari TV), kemudian `adb connect IP:PORT`.

### 1.3 Pasang APK dan beri kebenaran

Muat turun APK dari [Releases](https://github.com/chombeee/tv-g/releases/latest), kemudian:

```bash
adb install -r GlassTV.apk

# Lokasi (cuaca dan waktu solat)
adb shell pm grant com.faris.glasstv android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.faris.glasstv android.permission.ACCESS_COARSE_LOCATION

# Benarkan launcher hidupkan akses Notification sendiri (untuk Now Playing dan Lirik)
adb shell pm grant com.faris.glasstv android.permission.WRITE_SECURE_SETTINGS

# Recent Apps
adb shell appops set com.faris.glasstv GET_USAGE_STATS allow
adb shell pm grant com.faris.glasstv android.permission.DUMP

# Now Playing dan Lirik: akses Notification (jika TV tiada skrin Notification Access)
adb shell cmd notification allow_listener com.faris.glasstv/com.gathin.tvhome.ChombbMediaListener
```

Sesetengah kebenaran akan ditolak pada firmware tertentu. Itu normal. Lihat [Masalah lazim](#5-masalah-lazim).

---

## 2. Jadikan Glass TV sebagai skrin Home

Pilih **satu** kaedah ikut versi Android pada TV.

**Android 10 dan ke atas (disyorkan):**
```bash
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.faris.glasstv
```

**Android 9:**
```bash
adb shell cmd package set-home-activity com.faris.glasstv/com.gathin.tvhome.MainActivity
```

**Kalau TV masih buka launcher asal (firmware yang mengunci Home):**
Nyahaktifkan launcher asal **selepas** Glass TV dipasang dan dibuka sekurang-kurangnya sekali.

```bash
adb shell pm list packages | grep -i -E "launcher|home"     # cari nama package launcher asal
adb shell pm disable-user --user 0 <package-launcher-asal>
```

| Platform | Package launcher asal (semak dahulu, jangan salin membuta tuli) |
|---|---|
| Android TV | `com.google.android.tvlauncher`, `com.google.android.leanbacklauncher` |
| Google TV | `com.google.android.apps.tv.launcherx` |

**Cara pulih jika skrin jadi kosong:**
```bash
adb shell pm enable --user 0 <package-launcher-asal>
```

**Remote yang butang HOME tak berfungsi (contoh Xiaomi):** hidupkan **HomeKeyService** di *Settings → Accessibility*, atau:
```bash
adb shell settings put secure enabled_accessibility_services com.faris.glasstv/com.gathin.tvhome.HomeKeyService
adb shell settings put secure accessibility_enabled 1
```

---

## 3. Debloat (buang app yang tak perlu)

```bash
adb shell pm list packages -3        # app pengguna
adb shell pm list packages           # semua, termasuk sistem
```

Buang untuk pengguna semasa (boleh dipulihkan, tidak memadam partition sistem):
```bash
adb shell pm uninstall -k --user 0 <package>
```

Pulih semula:
```bash
adb shell cmd package install-existing <package>
```

Atau sekadar nyahaktif:
```bash
adb shell pm disable-user --user 0 <package>
adb shell pm enable --user 0 <package>
```

**Jangan buang:** `com.google.android.gms`, `com.android.vending`, `com.google.android.tv.remote.service`, setup wizard dan Settings. Buang satu demi satu dan uji TV selepas setiap pakej.

---

## 4. Kawalan remote

| Butang | Fungsi |
|---|---|
| OK pada app | Buka app |
| Tahan OK pada app | Menu: Move, App info, Sorok |
| Fokus awal | App pertama dalam dock |
| Kanan pada hujung dock | Masuk ke widget |
| HOME sekali | Kembali ke Home |
| HOME dua kali | Recent Apps |
| **Now Playing**: OK | Buka app yang sedang memainkan lagu |
| **Now Playing**: Tahan OK atau Menu | Simpan / Like lagu |
| **Lirik**: OK | Lirik skrin penuh |
| **Lirik skrin penuh**: Kiri / Kanan | Lagu sebelum / seterusnya |
| **Lirik skrin penuh**: OK | Main / jeda |
| **Lirik skrin penuh**: Back atau Home | Tutup |

### Muat naik wallpaper dari telefon
1. *Preferences → Appearance → Upload Wallpaper via QR*.
2. Imbas QR dengan telefon (mesti Wi-Fi yang sama).
3. Hantar gambar. **Biarkan tetingkap QR di TV terbuka** sehingga siap, kemudian tekan **Tutup**.

---

## 5. Masalah lazim

- **Now Playing kosong:** pastikan Notification Access untuk Glass TV hidup (arahan `allow_listener` di atas).
- **Lokasi atau waktu solat salah:** *Preferences → Lokasi Cuaca*.
- **Skrin kosong selepas disable launcher asal:** `adb shell pm enable --user 0 <package-launcher-asal>`.
- **Role HOME ditolak:** guna kaedah `pm disable-user` di bahagian 2.
- **OTA gagal kata tandatangan tak sama:** APK baru mesti ditandatangan dengan kunci yang sama seperti yang sedang dipasang. Jika tidak, nyahpasang dahulu (`adb uninstall com.faris.glasstv`) kemudian pasang semula.

---

## 6. Bina sendiri

Perlu **JDK 17** dan Android SDK.

**Windows (CMD):**
```bat
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

call gradlew.bat clean assembleRelease --no-daemon "-Dorg.gradle.java.home=%JAVA_HOME%"
```
APK siap di `app\build\outputs\apk\release\app-release.apk`.

**GitHub Actions:** tab **Actions → Build APK → Run workflow**, kemudian muat turun artifact `glasstv-apk`.

### Menerbitkan kemas kini OTA
1. **Releases → Draft a new release**, tag `vX.Y.Z`, muat naik APK, tekan **Publish release**.
2. Selepas itu, edit `release/version.json`:
```json
{
  "versionCode": 42,
  "versionName": "3.2.9",
  "apkUrl": "https://github.com/chombeee/tv-g/releases/download/v3.2.9/v3.2.9.apk",
  "changelog": "Ringkasan perubahan."
}
```
Edit `version.json` **selepas** release siap, supaya TV tidak cuba memuat turun pautan yang belum wujud. `versionCode` mesti lebih besar daripada versi yang sedang dipasang.

---

## Perubahan dalam versi ini (3.2.9)

- **Lirik skrin penuh:** blur dibuang. Launcher di belakang hanya digelapkan sedikit.
- **Now Playing:** tiada butang Play/Pause. Klik kad terus membuka app yang sedang memainkan lagu.
- **Now Playing Bar:** pilihan gaya **Kepala bercahaya** atau **Api** dalam tetapan widget.

### Dalam pembangunan
- Peraturan D-pad yang sama untuk keempat-empat layout (kiri/kanan kekal dalam baris, atas/bawah ke widget dan Settings).
- Animasi spring lembut yang seragam di seluruh UI.
- Widget Lirik yang bergerak sendiri: baris dibaca memudar, baris semasa terang dengan glow dan api, baris seterusnya gelap.

---

## Kredit dan lesen

- Berasaskan projek sumber terbuka **CleanYourTV** oleh Gathin (lesen MIT) dan **Glass TV** oleh aqmangge. Sila kekalkan notis hak cipta dan teks lesen asal dalam repo ini (fail `LICENSE`).
- Taip muka **Outfit** di bawah SIL Open Font License (`FONT-LICENSE-OFL.txt`).
- Pustaka warna Material di `third_party/material-color-utilities` mengikut lesen dalam folder tersebut.
- Gambar, logo dan nama app muzik adalah milik pemilik masing-masing.
