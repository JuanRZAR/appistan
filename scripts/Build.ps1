param([string[]]$Tasks = @('assembleDebug','testDebugUnitTest'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    $taskJdk = Get-ChildItem -Directory -LiteralPath (Join-Path $taskRoot '.tools/jdk') -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
    if (-not $env:JAVA_HOME) { throw 'Configura JAVA_HOME o prepara .tools/jdk.' }
    $taskSdk = Join-Path $taskRoot '.tools/android'
    if (Test-Path -LiteralPath $taskSdk) { $env:ANDROID_HOME = $taskSdk }
    if (-not $env:ANDROID_HOME) { throw 'Configura ANDROID_HOME con el SDK de Android.' }
    $env:GRADLE_USER_HOME = Join-Path $taskRoot '.tools/gradle-home'
    $env:ANDROID_USER_HOME = Join-Path $taskRoot '.tools/android-user'
    $env:GIT_CONFIG_COUNT = '1'
    $env:GIT_CONFIG_KEY_0 = 'safe.directory'
    $env:GIT_CONFIG_VALUE_0 = $taskRoot.Replace('\','/')
    $env:DEBUG = ''
    if (Test-Path -LiteralPath './gradlew.bat') { & ./gradlew.bat @Tasks --console=plain }
    else { & ./.tools/gradle-8.13/bin/gradle.bat @Tasks --console=plain }
    if ($LASTEXITCODE -ne 0) { throw "La compilación falló ($LASTEXITCODE)." }
} finally { Pop-Location }
