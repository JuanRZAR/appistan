param([string]$ReleaseDirectory)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskBun = Get-Command bun.exe,bun.cmd -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $taskBun) { throw 'Instala Bun y ejecuta bun install dentro de site/.' }
$taskPreviousRelease = $env:APPISTAN_RELEASE_DIR
$taskReleasePath = if ($ReleaseDirectory) { (Resolve-Path -LiteralPath $ReleaseDirectory).Path } else { $null }
Push-Location (Join-Path $taskRoot 'site')
try {
    if ($taskReleasePath) { $env:APPISTAN_RELEASE_DIR = $taskReleasePath }
    else { Remove-Item Env:APPISTAN_RELEASE_DIR -ErrorAction SilentlyContinue }
    & $taskBun.Source run build
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el sitio Astro.' }
    Write-Output ('Sitio estático preparado: ' + (Join-Path $taskRoot 'site/dist'))
} finally {
    if ($null -eq $taskPreviousRelease) { Remove-Item Env:APPISTAN_RELEASE_DIR -ErrorAction SilentlyContinue }
    else { $env:APPISTAN_RELEASE_DIR = $taskPreviousRelease }
    Pop-Location
}
