param([Parameter(Mandatory=$true)][ValidateSet('Major','Minor','Patch')][string]$Part)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$file = Join-Path $taskRoot 'version.properties'
$properties = ConvertFrom-StringData (Get-Content -Raw -LiteralPath $file)
$numbers = $properties.versionName.Split('.') | ForEach-Object { [int]$_ }
switch ($Part) {
    'Major' { $numbers[0]++; $numbers[1]=0; $numbers[2]=0 }
    'Minor' { $numbers[1]++; $numbers[2]=0 }
    'Patch' { $numbers[2]++ }
}
$version = $numbers -join '.'
$code = [int]$properties.versionCode + 1
[IO.File]::WriteAllText($file, "versionName=$version`nversionCode=$code`n", [Text.UTF8Encoding]::new($false))
Write-Output "Versión $version (versionCode $code). Actualiza CHANGELOG.md y crea el commit antes de publicar."
