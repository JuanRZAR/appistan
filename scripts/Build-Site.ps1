param([string]$ReleaseDirectory)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (-not $ReleaseDirectory) {
    $properties = ConvertFrom-StringData (Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'version.properties'))
    $ReleaseDirectory = Join-Path $taskRoot ('releases/' + $properties.versionName)
}
$metadataPath = Join-Path $ReleaseDirectory 'release.json'
$metadata = Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json
if ($metadata.artifact -notmatch '^AppIstan-\d+\.\d+\.\d+\.apk$') { throw 'Nombre de APK inválido.' }
$apk = Join-Path $ReleaseDirectory $metadata.artifact
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant() -ne $metadata.sha256) { throw 'El APK no coincide con los metadatos de la entrega.' }
if ((Get-Item -LiteralPath $apk).Length -gt 25MB) { throw 'El APK supera 25 MiB, el límite por archivo de Pages. Configura un alojamiento de APK externo antes de publicar.' }
$source = Join-Path $taskRoot 'site'
$dist = Join-Path $source 'dist'
New-Item -ItemType Directory -Force -Path (Join-Path $dist 'downloads') | Out-Null
foreach ($file in @('index.html','styles.css','app.js','_headers')) { Copy-Item -LiteralPath (Join-Path $source $file) -Destination (Join-Path $dist $file) -Force }
Copy-Item -LiteralPath (Join-Path $source 'assets') -Destination $dist -Recurse -Force
Copy-Item -LiteralPath $metadataPath -Destination (Join-Path $dist 'release.json') -Force
Copy-Item -LiteralPath $apk -Destination (Join-Path $dist ('downloads/' + $metadata.artifact)) -Force
Copy-Item -LiteralPath $apk -Destination (Join-Path $dist 'downloads/AppIstan-latest.apk') -Force
Write-Output "Sitio estático preparado: $dist"
