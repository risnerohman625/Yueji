param([switch]$Check)
$ErrorActionPreference='Stop'
$p=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME='D:\ProgramData\Android\AndroidStudio\jbr'
Set-Location $p
$tasks=@('assembleDebug','assembleDebugAndroidTest')
if($Check){$tasks+='lintDebug'}
& '.\gradlew.bat' @tasks --no-daemon --gradle-user-home (Join-Path $p 'gradle-home') --console plain
exit $LASTEXITCODE
