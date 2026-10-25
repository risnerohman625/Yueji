# Scratch: compiles the label sources (plus what they depend on) against android.jar and runs LabelRulesTest.
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$javaDir = 'D:\ProgramData\Android\AndroidStudio\jbr\bin'
$out = Join-Path $projectDir '.tools\label-scratch'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$cache = Join-Path $projectDir 'gradle-home\caches\modules-2\files-2.1'
$junit = (Get-ChildItem -LiteralPath (Join-Path $cache 'junit\junit\4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1).FullName
$hamcrest = (Get-ChildItem -LiteralPath (Join-Path $cache 'org.hamcrest\hamcrest-core\1.3') -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1).FullName
$androidJar = 'D:\ProgramData\Android\AndroidSDK\platforms\android-36\android.jar'

# Stub R with every drawable name so Ui.java compiles without aapt.
$stubDir = Join-Path $out 'stub'
New-Item -ItemType Directory -Force -Path $stubDir | Out-Null
$drawables = Get-ChildItem -Path (Join-Path $projectDir 'app\src\main\res') -Recurse -File |
    Where-Object { $_.Directory.Name -like 'drawable*' } | ForEach-Object { $_.BaseName } | Sort-Object -Unique
$fields = ($drawables | ForEach-Object { "  public static final int $_ = 0;" }) -join "`n"
[System.IO.File]::WriteAllText((Join-Path $stubDir 'R.java'), "package cn.yueji.balance;`npublic final class R {`n public static final class drawable {`n$fields`n }`n}`n", (New-Object System.Text.UTF8Encoding $false))

# The whole main source tree compiles together (other forks' files may be mid-edit, so allow only what this task needs).
$src = Join-Path $projectDir 'app\src\main\java\cn\yueji\balance'
$needed = 'LabelScreens','FlowLayout','LabelRules','Ui','Ledger','LedgerStore','FundCodec','FundEngine','FundClient','TradingCalendar','MainActivity'
$files = @()
# MainActivity drags in most of the app; compile everything except files that fail independently is not possible, so try the full tree first.
$all = Get-ChildItem -Path $src -Filter '*.java' | ForEach-Object { $_.FullName }
$tests = @(Join-Path $projectDir 'app\src\test\java\cn\yueji\balance\LabelRulesTest.java')
& "$javaDir\javac.exe" '-J-Dfile.encoding=UTF-8' '-J-Dstdout.encoding=UTF-8' '-J-Dstderr.encoding=UTF-8' -encoding UTF-8 -nowarn -cp "$androidJar;$junit;$hamcrest" -d $out ($all + (Join-Path $stubDir 'R.java') + $tests)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$javaDir\java.exe" '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp "$out;$androidJar;$junit;$hamcrest" org.junit.runner.JUnitCore cn.yueji.balance.LabelRulesTest
exit $LASTEXITCODE
