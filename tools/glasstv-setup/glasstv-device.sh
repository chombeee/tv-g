#!/system/bin/sh
set -f

GLASS=com.faris.glasstv
GLASS_HOME=$GLASS/com.gathin.tvhome.MainActivity
AUTO_PACKAGES='com.google.android.videos
com.google.android.play.games
com.google.android.music'

USER_ID=$(am get-current-user 2>/dev/null)
case "$USER_ID" in ''|*[!0-9]*) printf '%s\n' 'Gagal mengenal pasti pengguna TV.'; exit 1;; esac
STATE="/data/local/tmp/glasstv-setup-state-$USER_ID"
umask 077
mkdir -p "$STATE" || exit 1
HOME_BACKUP="$STATE/home-before"
DISABLED="$STATE/disabled"
[ -f "$DISABLED" ] || : > "$DISABLED"
mkdir "$STATE/lock" 2>/dev/null || { printf '%s\n' 'Sesi setup lain masih berjalan.'; exit 1; }
trap 'rmdir "$STATE/lock"' EXIT
trap 'exit 130' HUP INT TERM

home_component() {
    cmd package resolve-activity --brief --user "$USER_ID" \
        -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null |
        awk '/^[A-Za-z0-9_.]+\/[A-Za-z0-9_.$]+$/ { value=$0 } END { print value }'
}

set_home() {
    component=$1
    android_sdk=$(getprop ro.build.version.sdk)
    if [ "$android_sdk" -ge 29 ] 2>/dev/null; then
        cmd role add-role-holder --user "$USER_ID" android.app.role.HOME "${component%%/*}" 0
        [ "$(home_component)" = "$component" ] && return 0
    fi
    cmd package set-home-activity --user "$USER_ID" "$component"
    [ "$(home_component)" = "$component" ]
}

install_home() {
    if ! pm path --user "$USER_ID" "$GLASS" | grep -q '^package:'; then
        printf '%s\n' 'Pasang dan buka Glass TV pada TV dahulu.'
        return 1
    fi
    current=$(home_component)
    if [ "$current" = "$GLASS_HOME" ]; then
        printf '%s\n' 'Glass TV sudah menjadi Home.'
        return 0
    fi
    case "$current" in
        ''|android/*|com.android.internal.app/*)
            printf '%s\n' 'Home asal tidak dapat dikenal pasti. Pilih launcher asal sebagai Home dalam Settings dahulu supaya Undo boleh memulihkannya.'
            return 1;;
    esac
    [ -s "$HOME_BACKUP" ] || printf '%s\n' "$current" > "$HOME_BACKUP" || return 1
    if set_home "$GLASS_HOME"; then
        printf '%s\n' 'Glass TV disahkan sebagai Home.'
    else
        printf '%s\n' 'Firmware tidak mengesahkan Glass TV sebagai Home. Buka Settings TV dan pilih Default apps > Home jika tersedia.'
        if [ "$(home_component)" = "$current" ] || set_home "$current"; then
            [ "$(cat "$HOME_BACKUP")" != "$current" ] || rm -f "$HOME_BACKUP"
        else
            printf '%s\n' 'Home asal belum dipulihkan. Gunakan Undo semua untuk cuba semula.'
        fi
        return 1
    fi
}

is_protected() {
    case "$1" in
        "$GLASS"|android|com.android.*|com.google.android.gms|com.google.android.gsf|com.android.vending|*launcher*|*settings*|*setupwizard*|*remote*|*systemui*|*keyboard*|*inputmethod*|*webview*) return 0;;
    esac
    printf '%s\n' "$HOME_APPS" | grep -Fxq "$1"
}

enabled_packages() {
    pm list packages --user "$USER_ID" -e "$@" | sed -n 's/^package://p' | tr -d '\r' | sort
}

disable_package() {
    pkg=$1
    is_protected "$pkg" && return 0
    enabled_packages | grep -Fxq "$pkg" || return 0
    if ! grep -Fxq "$pkg" "$DISABLED"; then
        printf '%s\n' "$pkg" >> "$DISABLED" || return 1
    fi
    pm disable-user --user "$USER_ID" "$pkg"
    if ! pm list packages --user "$USER_ID" -d | grep -Fxq "package:$pkg"; then
        printf 'Gagal nyahaktifkan %s.\n' "$pkg"
        return 1
    fi
    printf 'Dinyahaktifkan: %s\n' "$pkg"
}

auto_candidates() {
    for pkg in $AUTO_PACKAGES; do
        if ! is_protected "$pkg" && enabled_packages | grep -Fxq "$pkg"; then
            printf '%s\n' "$pkg"
        fi
    done
}

confirm_auto() {
    printf '%s\n' "$1"
    packages=$(auto_candidates)
    if [ -n "$packages" ]; then printf 'Pakej yang akan dinyahaktifkan:\n%s\n' "$packages"
    else printf '%s\n' 'Tiada pakej debloat asas dikesan.'; fi
    printf 'Teruskan? [y/N] '
    read -r answer
    case "$answer" in y|Y) return 0;; *) return 1;; esac
}

auto_debloat() {
    printf '%s\n' 'Debloat asas.'
    packages=$(auto_candidates)
    [ -n "$packages" ] || { printf '%s\n' 'Tiada pakej pilihan yang aktif.'; return 0; }
    for pkg in $packages; do
        disable_package "$pkg" || printf 'Dilangkau: %s\n' "$pkg"
    done
}

manual_debloat() {
    candidates=$(enabled_packages -3 | while IFS= read -r pkg; do
        is_protected "$pkg" || printf '%s\n' "$pkg"
    done)
    if [ -z "$candidates" ]; then printf '%s\n' 'Tiada aplikasi pengguna yang boleh dipilih.'; return; fi
    printf '%s\n' "$candidates" | awk '{ print NR ". " $0 }'
    printf 'Nombor app (contoh 1 3), atau Enter untuk batal: '
    read -r numbers || return
    selected=''
    for number in $numbers; do
        case "$number" in ''|*[!0-9]*) printf '%s\n' 'Pilihan tidak sah.'; return;; esac
        pkg=$(printf '%s\n' "$candidates" | awk -v n="$number" 'NR==n { print; exit }')
        [ -n "$pkg" ] || { printf '%s\n' 'Pilihan di luar senarai.'; return; }
        selected="$selected $pkg"
    done
    [ -n "$selected" ] || return
    printf 'Nyahaktifkan%s? [y/N] ' "$selected"
    read -r answer
    case "$answer" in y|Y) for pkg in $selected; do disable_package "$pkg"; done;; esac
}

undo_debloat() {
    [ -s "$DISABLED" ] || { printf '%s\n' 'Tiada rekod debloat.'; return 0; }
    undo_remaining="$STATE/undo-remaining"
    : > "$undo_remaining" || return 1
    undo_scope=${1:-all}
    while IFS= read -r pkg; do
        case "$pkg" in ''|*[!A-Za-z0-9_.]*) continue;; esac
        if [ "$undo_scope" != all ] && [ "$undo_scope" != "$pkg" ]; then
            printf '%s\n' "$pkg" >> "$undo_remaining"
            continue
        fi
        if enabled_packages | grep -Fxq "$pkg" ||
                { pm enable --user "$USER_ID" "$pkg" && enabled_packages | grep -Fxq "$pkg"; }; then
            printf 'Aktif semula: %s\n' "$pkg"
        else
            printf '%s\n' "$pkg" >> "$undo_remaining"
            printf 'Belum dipulihkan: %s\n' "$pkg"
        fi
    done < "$DISABLED"
    mv "$undo_remaining" "$DISABLED"
    if [ "$undo_scope" = all ]; then [ ! -s "$DISABLED" ]; else ! grep -Fxq "$undo_scope" "$DISABLED"; fi
}

undo_home() {
    [ -s "$HOME_BACKUP" ] || { printf '%s\n' 'Tiada perubahan Home dalam rekod.'; return 0; }
    original=$(cat "$HOME_BACKUP")
    if [ "$(home_component)" = "$original" ] || set_home "$original"; then
        rm -f "$HOME_BACKUP"
        printf 'Home dipulihkan: %s\n' "$original"
    else
        printf 'Home asal belum dapat dipulihkan: %s\n' "$original"
        return 1
    fi
}

undo_prompt() {
    printf '\n1. Kekalkan perubahan\n2. Undo debloat\n3. Undo semua (debloat dan Home)\n4. Pulihkan satu app\nPilihan: '
    read -r choice || return 0
    case "$choice" in
        2) undo_debloat;;
        3) undo_debloat; debloat_result=$?; undo_home && [ "$debloat_result" -eq 0 ];;
        4)
            [ -s "$DISABLED" ] || { printf '%s\n' 'Tiada rekod debloat.'; return; }
            awk '{ print NR ". " $0 }' "$DISABLED"
            printf 'Nombor app: '
            read -r number || return
            case "$number" in ''|*[!0-9]*) return 1;; esac
            pkg=$(awk -v n="$number" 'NR==n { print; exit }' "$DISABLED")
            [ -n "$pkg" ] && undo_debloat "$pkg";;
    esac
}

HOME_APPS=$(cmd package query-activities --brief --user "$USER_ID" \
    -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null |
    awk -F/ '/^[A-Za-z0-9_.]+\// { print $1 }')
original=$(home_component)
HOME_APPS="$HOME_APPS
${original%%/*}"
if [ -s "$HOME_BACKUP" ]; then
    saved_home=$(cat "$HOME_BACKUP")
    HOME_APPS="$HOME_APPS
${saved_home%%/*}"
fi

case "$1" in
    auto)
        if install_home; then auto_debloat; undo_prompt
        else undo_prompt; exit 1; fi;;
    menu)
        printf '1. Setup Auto (Home dan debloat)\n2. Tetapkan Home sahaja\n3. Debloat Auto sahaja\n4. Debloat Manual\n5. Undo / pulihkan app\nEnter untuk keluar\nPilihan: '
        read -r choice || exit 0
        case "$choice" in
            1) if confirm_auto 'Tetapkan Glass TV sebagai Home dan jalankan debloat asas?'; then
                   if install_home; then auto_debloat; fi
                   undo_prompt
               fi;;
            2) install_home; undo_prompt;;
            3) if confirm_auto 'Jalankan debloat asas sahaja?'; then auto_debloat; undo_prompt; fi;;
            4) manual_debloat; undo_prompt;;
            5) undo_prompt;;
        esac;;
    undo) undo_prompt;;
    *) exit 2;;
esac
