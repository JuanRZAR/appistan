$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    $gitArguments = @('-c', ('safe.directory=' + $taskRoot.Replace('\','/')))
    $dirty = & git @gitArguments status --porcelain
    if ($dirty) { throw 'Crea un commit de los cambios antes de generar una entrega trazable.' }
    $properties = ConvertFrom-StringData (Get-Content -Raw version.properties)
    $version = $properties.versionName
    $commit = (& git @gitArguments rev-parse HEAD).Trim()
    $existingTag = & git @gitArguments tag --list "v$version"
    if ($existingTag) { throw "La versión v$version ya tiene etiqueta. Incrementa la versión antes de publicar otra entrega." }
    if (-not $env:APPISTAN_KEYSTORE_PASSWORD) {
        $secure = Get-Content -Raw keystore/password.dpapi | ConvertTo-SecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:APPISTAN_KEYSTORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
    & ./scripts/Build.ps1 -Tasks @(':app:testDebugUnitTest', ':app:assembleRelease')
    $directory = Join-Path $taskRoot "releases/$version"
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $fileName = "AppIstan-$version.apk"
    $artifact = Join-Path $directory $fileName
    Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination $artifact
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $directory 'AppIstan-latest.apk')
    $metadata = [ordered]@{ version=$version; versionCode=[int]$properties.versionCode; artifact=$fileName; sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $artifact).Hash.ToLowerInvariant(); sizeBytes=(Get-Item -LiteralPath $artifact).Length; gitCommit=$commit; createdUtc=[DateTime]::UtcNow.ToString('o') }
    [IO.File]::WriteAllText((Join-Path $directory 'release.json'), ($metadata | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $directory "$fileName.sha256"), ($metadata.sha256 + '  ' + $fileName + "`n"), [Text.UTF8Encoding]::new($false))
    & ./scripts/Build-Site.ps1 -ReleaseDirectory $directory
    & git @gitArguments tag -a "v$version" -m "AppIstan $version"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la etiqueta de la entrega.' }
    Write-Output "Entrega lista: $artifact"
} finally {
    Remove-Item Env:APPISTAN_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
