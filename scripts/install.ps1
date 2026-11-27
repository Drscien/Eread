param(
    [string]$WorkDir = "$env:USERPROFILE\Desktop",
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $PSScriptRoot
$legado = Join-Path $WorkDir "legado"

if (-not (Test-Path $legado)) {
    Write-Host "cloning legado ..."
    git clone --depth 1 https://github.com/LegadoTeam/legado.git $legado
}

Write-Host "copying patch ..."
Copy-Item (Join-Path $here "patch\app\*") (Join-Path $legado "app") -Recurse -Force

Write-Host "raising minSdk 23 -> 26 (htmlunit)"
(Get-Content (Join-Path $legado "app\build.gradle")) -replace 'minSdk 23', 'minSdk 26' | Set-Content (Join-Path $legado "app\build.gradle")

$manifest = Join-Path $legado "app\src\main\AndroidManifest.xml"
$py = Get-Command python -ErrorAction SilentlyContinue
if ($py) {
    python (Join-Path $here "scripts\inject_manifest.py") $manifest
} else {
    Write-Warning "python not found, inject provider manually: see patch/app/src/main/AndroidManifest.snippet.xml"
}

if ($Build) {
    Push-Location $legado
    try {
        .\gradlew.bat assembleDebug
        Write-Host "APK: $legado\app\build\outputs\apk\debug\"
    } finally {
        Pop-Location
    }
}
