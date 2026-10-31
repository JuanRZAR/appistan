$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $taskRoot 'keystore'
$keyFile = Join-Path $keyDirectory 'appistan.jks'
if (Test-Path -LiteralPath $keyFile) { throw 'La clave ya existe. Se conserva para permitir actualizaciones.' }
$taskJdk = (Get-ChildItem -Directory -LiteralPath (Join-Path $taskRoot '.tools/jdk') | Select-Object -First 1).FullName
New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
$secretBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($secretBytes) } finally { $rng.Dispose() }
$env:APPISTAN_KEYSTORE_PASSWORD = [Convert]::ToBase64String($secretBytes)
try {
    $encrypted = ConvertTo-SecureString $env:APPISTAN_KEYSTORE_PASSWORD -AsPlainText -Force | ConvertFrom-SecureString
    [IO.File]::WriteAllText((Join-Path $keyDirectory 'password.dpapi'), $encrypted)
    & (Join-Path $taskJdk 'bin/keytool.exe') -genkeypair -keystore $keyFile -storetype JKS -alias appistan -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=AppIstan, O=AppIstan, C=EC' -storepass:env APPISTAN_KEYSTORE_PASSWORD -keypass:env APPISTAN_KEYSTORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la clave de firma.' }
    Write-Output 'Clave creada. Conserva keystore/ y prepara una copia de seguridad de la clave y su contraseña.'
} finally { Remove-Item Env:APPISTAN_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue }
