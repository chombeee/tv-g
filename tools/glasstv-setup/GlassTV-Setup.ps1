$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$adbPath = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($adbPath) { $adb = $adbPath.Source }
elseif (Test-Path '.\platform-tools\adb.exe') { $adb = (Resolve-Path '.\platform-tools\adb.exe').Path }
else { throw 'Pasang Android Platform Tools dan tambah adb ke PATH, atau letak folder platform-tools di sebelah fail BAT.' }

$devices = @(& $adb devices | ForEach-Object {
    if ($_ -match '^([^\s]+)\s+device\s*$') { $Matches[1] }
})
switch ($devices.Count) {
    0 {
        $serial = Read-Host 'Alamat ADB TV (IP:PORT)'
        if ($serial -notmatch '^[a-zA-Z0-9][a-zA-Z0-9.:-]*$') { throw 'Alamat tidak sah.' }
        & $adb connect $serial
    }
    1 { $serial = $devices[0] }
    default {
        & $adb devices
        $serial = Read-Host 'Serial TV yang hendak digunakan'
        if ($serial -notin $devices) { throw 'Pilih serial daripada senarai peranti.' }
    }
}
$state = & $adb -s $serial get-state
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') { throw 'TV belum bersambung. Semak ADB dan tekan Allow debugging pada TV.' }
$remote = "/data/local/tmp/glasstv-setup-$PID.sh"
try {
    & $adb -s $serial push 'glasstv-device.sh' $remote
    if ($LASTEXITCODE -ne 0) { throw 'Skrip TV gagal disalin.' }
    & $adb -s $serial shell -t sh $remote menu
    $result = $LASTEXITCODE
} finally {
    & $adb -s $serial shell rm -f $remote | Out-Null
}
exit $result
