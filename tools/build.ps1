param([switch]$Check)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'D:\ProgramData\Android\AndroidStudio\jbr'
Push-Location $projectDir
try {
    $buildTasks = @('assembleDebug')
    if ($Check) { $buildTasks += 'lintDebug' }
    & '.\gradlew.bat' @buildTasks --no-daemon --gradle-user-home (Join-Path $projectDir 'gradle-home') --console plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Output (Join-Path $projectDir 'app\build\outputs\apk\debug\app-debug.apk')
} finally { Pop-Location }
