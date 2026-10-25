param([string]$Serial='', [string]$Classes='', [switch]$SkipInstall)
$ErrorActionPreference='Stop'
$p=Split-Path -Parent $PSScriptRoot
$adb='D:\ProgramData\Android\AndroidSDK\platform-tools\adb.exe'
if(-not $Serial){
    $devices=@(& $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device\b' })
    if($devices.Count -ne 1){throw 'Specify one running emulator with -Serial emulator-5554. This script does not run on real phones.'}
    $Serial=($devices[0] -split '\s+')[0]
}
if($Serial -notmatch '^emulator-\d+$'){throw 'Only emulator targets are allowed; never replace a real user ledger with test fixtures.'}
Push-Location $p
try{
    if(-not $SkipInstall){
        foreach($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')){
            & $adb -s $Serial install -r $apk
            if($LASTEXITCODE -ne 0){throw "APK installation failed: $apk"}
        }
    }
    $arguments=@('-s',$Serial,'shell','am','instrument','-w')
    if($Classes){$arguments+=@('-e','class',$Classes)}
    $arguments+='cn.yueji.balance.test/androidx.test.runner.AndroidJUnitRunner'
    & $adb @arguments 2>&1 | Tee-Object -Variable output
    New-Item -ItemType Directory -Force -Path 'docs/validation' | Out-Null
    $report=Join-Path $p 'docs/validation/device-tests.txt'
    [IO.File]::WriteAllLines($report,[string[]]$output,[Text.UTF8Encoding]::new($false))
    New-Item -ItemType Directory -Force -Path 'docs/screenshots-v470' | Out-Null
    & "$adb" -s $Serial pull /sdcard/Android/data/cn.yueji.balance/files/qa-v470/. docs/screenshots-v470/
    if(($output -join "`n") -notmatch 'OK \(\d+ tests?\)'){throw "Instrumentation did not pass. See $report"}
}finally{Pop-Location}
