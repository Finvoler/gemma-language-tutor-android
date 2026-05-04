$ErrorActionPreference = 'Stop'

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $models = Join-Path (Get-Location) 'models'
    New-Item -ItemType Directory -Force -Path $models | Out-Null

    $fileName = 'gemma-4-E2B-it.litertlm'
    $url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$fileName`?download=true"
    $output = Join-Path $models $fileName

    Write-Host "Downloading Gemma 4 E2B LiteRT-LM model to: $output"
    Write-Host 'This file is about 2.58 GB.'
    Invoke-WebRequest -Uri $url -OutFile $output

    $item = Get-Item $output
    Write-Host ('Downloaded {0:N2} GB' -f ($item.Length / 1GB))
} finally {
    Pop-Location
}