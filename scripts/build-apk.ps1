$ErrorActionPreference = 'Stop'

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $localSdk = Join-Path (Get-Location) '.android-sdk'
    if (Test-Path (Join-Path $localSdk 'platforms\android-35')) {
        $env:ANDROID_SDK_ROOT = $localSdk
        $env:ANDROID_HOME = $localSdk
    }

    $localGradle = Join-Path (Get-Location) '.gradle-local\gradle-8.10.2\bin\gradle.bat'
    if (Test-Path $localGradle) {
        & $localGradle assembleDebug
    } elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
        gradle assembleDebug
    } else {
        throw 'Gradle was not found. Install Android Studio/Gradle or use the bundled .gradle-local directory.'
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE."
    }

    $apk = Join-Path (Get-Location) 'app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path $apk) {
        Write-Host "APK generated: $apk"
    } else {
        throw 'Gradle finished but app-debug.apk was not found.'
    }
} finally {
    Pop-Location
}