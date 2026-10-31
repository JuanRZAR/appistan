param([int]$Port = 5555)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    $adb = Join-Path $taskRoot '.tools/android/platform-tools/adb.exe'
    $serial = "127.0.0.1:$Port"
    function Invoke-TestAdb {
        $ErrorActionPreference = 'Continue'
        & $adb -P 5038 @args
        if ($LASTEXITCODE -ne 0) { throw "ADB falló ($LASTEXITCODE)." }
    }
    Invoke-TestAdb connect $serial
    Invoke-TestAdb -s $serial shell getprop ro.build.version.release
    & ./scripts/Build.ps1 -Tasks @(':testfixture:assembleDebug','-PfixtureVersionCode=1')
    Invoke-TestAdb -s $serial install -r -d testfixture/build/outputs/apk/debug/testfixture-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la fixture v1 de desarrollo.' }
    Invoke-TestAdb -s $serial shell am start -n com.appistan.fixture/.FixtureActivity
    Start-Sleep -Seconds 1
    New-Item -ItemType Directory -Force -Path .tools/test-assets | Out-Null
    Copy-Item -LiteralPath testfixture/build/outputs/apk/debug/testfixture-debug.apk -Destination .tools/test-assets/fixture-v1.apk
    & ./scripts/Build.ps1 -Tasks @(':testfixture:assembleDebug','-PfixtureVersionCode=2')
    New-Item -ItemType Directory -Force -Path .tools/test-assets | Out-Null
    Copy-Item -LiteralPath testfixture/build/outputs/apk/debug/testfixture-debug.apk -Destination .tools/test-assets/fixture-v2.apk
    & ./scripts/Build.ps1 -Tasks @(':app:assembleDebug',':app:testDebugUnitTest')
    Invoke-TestAdb -s $serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar AppIstan.' }
    $ErrorActionPreference = 'Continue'
    & node ./scripts/test-device.cjs $adb $serial
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas ADB.' }
} finally { Pop-Location }
