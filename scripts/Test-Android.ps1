param(
    [Parameter(Mandatory)][ValidateSet('phone','watch')][string]$Module,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [string]$AdbPath
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if(-not $AdbPath) {
    $sdk = $env:ANDROID_HOME
    if(-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if(-not $sdk) {
        $properties = Join-Path $projectRoot 'local.properties'
        if(Test-Path -LiteralPath $properties) {
            $line = Get-Content -LiteralPath $properties | Where-Object {$_ -match '^sdk\.dir='} | Select-Object -First 1
            if($line) { $sdk = $line.Substring(8).Replace('\:',':').Replace('\\','\') }
        }
    }
    if(-not $sdk) { throw 'Set ANDROID_HOME or pass -AdbPath pointing to adb.exe.' }
    $AdbPath = Join-Path $sdk 'platform-tools\adb.exe'
}
if(-not(Test-Path -LiteralPath $AdbPath)) { throw "ADB not found: $AdbPath" }
$state = & $AdbPath -s $Serial get-state 2>&1
if($LASTEXITCODE -ne 0 -or ($state -join '').Trim() -ne 'device') { throw "Device is not ready: $Serial" }
$features = & $AdbPath -s $Serial shell pm list features
if($LASTEXITCODE -ne 0) { throw 'Cannot read device features.' }
$isWatch = [bool]($features -match 'android.hardware.type.watch')
if(($Module -eq 'watch') -ne $isWatch) { throw "Selected $Serial is not a $Module test device." }
$package = 'nodomain.greensnowapps.lightsignals.uitest'
$previousSerial = $env:ANDROID_SERIAL
$wakeJob = $null
Push-Location $projectRoot
try {
    $env:ANDROID_SERIAL = $Serial
    if($Module -eq 'watch') {
        $wakeJob = Start-Job -ScriptBlock {
            param($adb, $serial)
            while($true) {
                & $adb -s $serial shell input keyevent KEYCODE_WAKEUP *> $null
                Start-Sleep -Seconds 5
            }
        } -ArgumentList $AdbPath, $Serial
    }
    & '.\gradlew.bat' ":${Module}:assembleUitest" ":${Module}:assembleUitestAndroidTest" --stacktrace
    if($LASTEXITCODE -ne 0) { throw 'Android test APK build failed.' }
    $metadataPath = Join-Path $projectRoot "$Module\build\outputs\apk\uitest\output-metadata.json"
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if($metadata.applicationId -ne $package) { throw 'Refusing to test an APK without the isolated .uitest app ID.' }
    # Orchestrator isolates subsequent cases; clear an existing test installation before the first case.
    $installed = & $AdbPath -s $Serial shell pm path $package
    if($installed -match '^package:') {
        $cleared = & $AdbPath -s $Serial shell pm clear $package
        if($LASTEXITCODE -ne 0 -or ($cleared -join '').Trim() -ne 'Success') { throw 'Cannot reset the isolated test application.' }
    }
    & '.\gradlew.bat' ":${Module}:connectedUitestAndroidTest" --stacktrace
    if($LASTEXITCODE -ne 0) { throw "Android tests failed; inspect $Module\build\reports\androidTests\connected." }
} finally {
    if($wakeJob) {
        Stop-Job -Job $wakeJob
        Remove-Job -Job $wakeJob
    }
    Pop-Location
    $env:ANDROID_SERIAL = $previousSerial
}
