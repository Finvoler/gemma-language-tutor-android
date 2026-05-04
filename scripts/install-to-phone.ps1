$ErrorActionPreference = 'Stop'

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $localAdb = Join-Path (Get-Location) '.android-sdk\platform-tools\adb.exe'
    if (Test-Path $localAdb) {
        $adb = $localAdb
    } elseif (Get-Command adb -ErrorAction SilentlyContinue) {
        $adb = 'adb'
    } else {
        throw 'adb was not found. Install Android SDK platform-tools or use the bundled .android-sdk directory.'
    }
    $apk = Join-Path (Get-Location) 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) {
        throw 'APK was not found. Run .\scripts\build-apk.ps1 first.'
    }
    & $adb install -r $apk
} finally {
    Pop-Location
}