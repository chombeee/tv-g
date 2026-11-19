#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

mode=auto
if [[ ${1:-} == --undo ]]; then mode=undo; shift; fi
if (( $# > 1 )); then printf '%s\n' 'Guna: bash glasstv-setup.sh [--undo] [IP:PORT]'; exit 2; fi
if ! command -v adb >/dev/null; then
    if command -v pkg >/dev/null; then pkg install -y android-tools
    else printf '%s\n' 'Pasang Android Platform Tools dahulu.'; exit 1; fi
fi

address=${1:-}
if [[ -z $address ]]; then
    mapfile -t devices < <(adb devices | awk '$2=="device" { print $1 }')
    case ${#devices[@]} in
        0) read -rp 'Alamat ADB TV (IP:PORT): ' address;;
        1) serial=${devices[0]};;
        *) adb devices; read -rp 'Serial TV yang hendak digunakan: ' serial;;
    esac
fi
if [[ -n $address ]]; then
    [[ $address =~ ^[0-9A-Za-z][0-9A-Za-z.:-]*$ ]] || { echo 'Alamat tidak sah.'; exit 1; }
    adb connect "$address"
    serial=$address
fi
if [[ ${serial:-} == -* || $(adb -s "${serial:-}" get-state 2>/dev/null || true) != device ]]; then
    printf '%s\n' 'TV belum bersambung. Semak alamat ADB dan tekan Allow debugging pada TV.'
    exit 1
fi
remote="/data/local/tmp/glasstv-setup-$$.sh"
trap 'adb -s "$serial" shell rm -f "$remote" >/dev/null 2>&1 || true' EXIT
adb -s "$serial" push glasstv-device.sh "$remote"
adb -s "$serial" shell -t sh "$remote" "$mode"
