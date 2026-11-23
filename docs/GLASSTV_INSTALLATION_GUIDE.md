# Glass TV CHOMBB — Installation & Recovery Guide

Panduan pemasangan selamat untuk Android TV, Google TV dan TV Box. Repositori rasmi: https://github.com/chombeee/tv-g

> **Penting:** Memasang APK dan menetapkannya sebagai aplikasi HOME ialah dua proses berbeza. Keputusan ADB `Success` tidak menjamin butang Home pada remote akan membuka GlassTV. Sesetengah firmware Google TV (termasuk Chromecast with Google TV 4K pada Android 14) mungkin masih memilih launcher asal atau `RecoveryActivity` walaupun HOME role menunjukkan GlassTV. **Jangan disable `setupwraith`, Google Play Services, Cast, Wi-Fi, Settings atau System UI.**

## A. Keperluan

- TV berasaskan Android/Google TV (projek CHOMBB menyatakan Android 8 / API 26 ke atas); bukan Samsung Tizen / LG webOS.
- Telefon Android dengan Termux atau PC dengan Android Platform Tools.
- Telefon/PC dan TV berada dalam rangkaian yang boleh saling menghubungi.
- Developer Options dan Wireless Debugging (jika disokong) atau kaedah ADB yang tersedia pada peranti.

## B. Sambung Termux kepada TV

Dalam Termux:

```bash
pkg update
pkg install android-tools curl jq -y
```

Di TV, buka **Settings > System > About**, tekan *Build* 7 kali jika perlu, kemudian hidupkan debugging dalam Developer Options.

Jika TV menyediakan **Wireless Debugging > Pair device with pairing code**, gunakan **pairing IP:port** dan kod yang TV paparkan:

```bash
adb pair TV_IP:PAIRING_PORT
```

Selepas berjaya, keluar daripada paparan pairing. Cari **IP address & Port** pada halaman utama Wireless Debugging. Port ini **berbeza** daripada pairing port:

```bash
adb connect TV_IP:ADB_PORT
adb devices -l
```

Teruskan hanya jika status ialah `device`. Jika `offline`, semak pairing/port. Jika `No route to host`, semak rangkaian/IP TV; jangan ulang pairing membuta tuli. Port boleh bertukar apabila debugging atau Wi-Fi berubah. Untuk TV yang menyokong ADB TCP klasik, port mungkin `5555`, tetapi jangan menganggap semua TV menggunakannya.

Simpan alamat sambungan yang betul dalam satu pemboleh ubah:

```bash
TV='TV_IP:ADB_PORT'
adb -s "$TV" shell getprop ro.product.model
adb -s "$TV" shell getprop ro.build.version.release
adb -s "$TV" shell wm size
adb -s "$TV" shell wm density
```

## C. Download dan install APK rasmi

```bash
mkdir -p ~/glasstv-install && cd ~/glasstv-install
URL=$(curl -fsSL https://api.github.com/repos/chombeee/tv-g/releases/latest | jq -r '[.assets[] | select(.name | ascii_downcase | endswith(".apk"))][0].browser_download_url // empty')
if [ -z "$URL" ]; then
  echo 'APK tiada dalam GitHub Release terbaru. Semak halaman Releases.'
else
  echo "Downloading: $URL"
  curl -fL "$URL" -o GlassTV.apk && adb -s "$TV" install -r GlassTV.apk
fi
```

Semak `Success` dan pakej `com.faris.glasstv`:

```bash
adb -s "$TV" shell pm path com.faris.glasstv
```

**Jangan disable launcher asal pada tahap ini.**

## D. Buka GlassTV dan uji dahulu

```bash
GLASS='com.faris.glasstv/com.gathin.tvhome.MainActivity'
adb -s "$TV" shell am start -W -n "$GLASS"
```

Uji D-pad, OK, Back, buka aplikasi lain, Wi-Fi dan Settings. `adb shell monkey -p ... -c android.intent.category.LAUNCHER 1` mungkin gagal kerana GlassTV menggunakan HOME activity; gunakan komponen di atas.

## E. Pilih GlassTV sebagai Home (pilihan)

1. Jika TV menyediakan menu **Default apps > Home app**, pilih GlassTV di sana dahulu.
2. Jika tidak, cuba kaedah ADB di bawah. Ini **tidak dijamin** pada semua firmware:

```bash
# Simpan keadaan sebelum cuba
mkdir -p ~/glasstv-backup
adb -s "$TV" shell cmd role get-role-holders android.app.role.HOME | tee ~/glasstv-backup/home-role-before.txt
adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tee ~/glasstv-backup/home-resolver-before.txt
adb -s "$TV" shell pm list packages -d | tee ~/glasstv-backup/disabled-before.txt

# Cuba pilih GlassTV
adb -s "$TV" shell cmd package set-home-activity com.faris.glasstv/com.gathin.tvhome.MainActivity
adb -s "$TV" shell cmd role get-role-holders android.app.role.HOME
```

Uji Home dan **periksa activity sebenar**:

```bash
adb -s "$TV" shell input keyevent KEYCODE_HOME
adb -s "$TV" shell dumpsys activity activities | grep -E 'ResumedActivity:|mCurrentFocus=|mFocusedApp=' | tail -10
```

Jika activity yang aktif bukan `com.faris.glasstv`, pemasangan sebagai Home **belum berjaya**. Jangan anggap arahan `Success` sudah mencukupi.

### Amaran untuk Google TV / Chromecast Android 14

Pada sesetengah peranti, HOME role boleh menunjukkan `com.faris.glasstv` walaupun menekan Home masih membuka `com.google.android.apps.tv.launcherx`. Menyahaktifkan `launcherx` pula boleh mengalihkan Home kepada `com.google.android.tungsten.setupwraith/.RecoveryActivity`. Oleh itu panduan rasmi ini **tidak mencadangkan** auto-disable stock launcher untuk memaksa Home. Kekalkan launcher asal sebagai fallback dan terangkan batasan firmware kepada pengguna.

## F. Debloat pilihan — hanya selepas Home dan aplikasi stabil

Debloat **bukan syarat** pemasangan GlassTV. Jangan jalankan debloat automatik sebelum audit pakej dan persetujuan pengguna. Rekod keadaan asal dan setiap pakej yang benar-benar diubah. Untuk mula, semak:

```bash
adb -s "$TV" shell pm list packages -s > ~/glasstv-backup/system-packages-before.txt
adb -s "$TV" shell pm list packages -d > ~/glasstv-backup/disabled-before-debloat.txt
```

Sebagai contoh, aplikasi bukan teras yang boleh *dipertimbangkan* mengikut kegunaan termasuk `com.google.android.feedback`, `com.google.android.play.games` dan `com.google.android.videos`. Kehadiran pakej tidak bermakna ia selamat disable pada setiap firmware. Uji satu demi satu. Jangan sentuh: Play Services (`com.google.android.gms`), Cast (`com.google.android.apps.mediashell` dan servis Chromecast), `setupwraith`, System UI, Settings, Wi-Fi/Bluetooth, dan servis remote. Jangan menjanjikan penjimatan RAM tertentu tanpa mengukur penggunaan sebelum/selepas.

## G. Undo / Recovery

Jika TV membuka RecoveryActivity atau Home bermasalah, gunakan ADB yang masih connected untuk mengaktifkan semula launcher asal **jika pakej tersebut sememangnya telah di-disable**:

```bash
adb -s "$TV" shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb -s "$TV" shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
adb -s "$TV" shell input keyevent KEYCODE_HOME
```

Untuk peranti bukan Chromecast, **jangan guna nama pakej Google di atas tanpa mengesahkan launcher asal**. Guna pakej yang direkodkan semasa audit. Pulihkan pakej debloat yang benar-benar diubah berdasarkan log perubahan, bukan enable semua pakej secara rambang. Jika prestasi merosot, hentikan debloat, pulihkan perubahan dan uji selepas reboot.

## H. Semakan akhir

- `adb devices -l` menunjukkan `device` sebelum menjalankan arahan ADB.
- GlassTV membuka dengan `am start -n ...` dan remote berfungsi.
- Home pada remote benar-benar membuka GlassTV; semak activity aktif, bukan hanya HOME role.
- YouTube/Netflix, Wi-Fi, Bluetooth, Cast dan Settings berfungsi.
- Ujian selepas reboot; jika Home kembali ke launcher asal, terangkan had firmware dan jangan disable komponen pemulihan.
- Sediakan nota jelas cara Undo untuk semua langkah pilihan.

### Rujukan

- [Projek Glass TV aqmangge](https://github.com/aqmangge/glass-tv-home) — inspirasi susunan panduan/audit; sesuaikan nama pakej untuk CHOMBB.
- [Glass TV CHOMBB](https://github.com/chombeee/tv-g)

Panduan ini tidak memasang atau mengubah aplikasi secara automatik. Ia bertujuan untuk digunakan dalam README atau `docs/INSTALLATION.md`, selepas diuji pada model TV sasaran.
