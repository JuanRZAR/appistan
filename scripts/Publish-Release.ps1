param([string]$ReleaseDirectory)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskBun = Get-Command bun.exe,bun.cmd -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $taskBun) { throw 'Instala Bun para publicar la entrega.' }
$taskArguments = @('run', (Join-Path $taskRoot 'scripts/publish-release.ts'))
if ($ReleaseDirectory) { $taskArguments += @('--release-dir', (Resolve-Path -LiteralPath $ReleaseDirectory).Path) }
& $taskBun.Source @taskArguments
if ($LASTEXITCODE -ne 0) { throw 'La publicación no terminó. Los archivos locales se conservan; reintenta Publish-Release.ps1.' }
