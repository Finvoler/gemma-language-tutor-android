$ErrorActionPreference = 'Stop'

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $tempRoot = Join-Path (Get-Location) '_download_tmp\tts'
    if (Test-Path $tempRoot) {
        Remove-Item -Recurse -Force $tempRoot
    }
    New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

    $assetsRoot = Join-Path (Get-Location) 'app\src\main\assets'
    $amyTarget = Join-Path $assetsRoot 'tts-model'

    $legacyKokoroTarget = Join-Path $assetsRoot 'tts-model-kokoro-en'
    if (Test-Path $legacyKokoroTarget) {
        Remove-Item -Recurse -Force $legacyKokoroTarget
    }

    $downloads = @(
        @{
            Name = 'Piper Amy';
            Url = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2';
            Archive = 'vits-piper-en_US-amy-medium.tar.bz2';
            ExtractedDir = 'vits-piper-en_US-amy-medium';
            TargetDir = $amyTarget;
        }
    )

    foreach ($item in $downloads) {
        $archivePath = Join-Path $tempRoot $item.Archive
        Write-Host "Downloading $($item.Name) ..."
        curl.exe -L $item.Url -o $archivePath

        Write-Host "Extracting $($item.Archive) ..."
        tar -xf $archivePath -C $tempRoot

        $sourceDir = Join-Path $tempRoot $item.ExtractedDir
        if (-not (Test-Path $sourceDir)) {
            throw "Extracted directory not found: $sourceDir"
        }

        if (Test-Path $item.TargetDir) {
            Remove-Item -Recurse -Force $item.TargetDir
        }
        New-Item -ItemType Directory -Force -Path $item.TargetDir | Out-Null

        Copy-Item -Recurse -Force (Join-Path $sourceDir '*') $item.TargetDir
    }

    Write-Host ''
    Write-Host 'Amy TTS assets are ready:'
    Write-Host "  $amyTarget"
} finally {
    Pop-Location
}
