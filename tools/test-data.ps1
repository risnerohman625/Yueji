param([switch]$Generate)
# Compiles only the non-UI sources plus all tests, so the data-format layer can be tested while UI files are being edited.
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$javaDir = 'D:\ProgramData\Android\AndroidStudio\jbr\bin'
$compiledDir = Join-Path $projectDir '.tools\data-tests'
$resolved=[IO.Path]::GetFullPath($compiledDir);if(-not $resolved.StartsWith([IO.Path]::GetFullPath($projectDir)+[IO.Path]::DirectorySeparatorChar)){throw "Unsafe test output directory"}
if (Test-Path -LiteralPath $compiledDir) { Remove-Item -LiteralPath $compiledDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $compiledDir | Out-Null
$gradleCache = Join-Path $projectDir 'gradle-home\caches\modules-2\files-2.1'
$junitJar = Get-ChildItem -LiteralPath (Join-Path $gradleCache 'junit\junit\4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1 -ExpandProperty FullName
$hamcrestJar = Get-ChildItem -LiteralPath (Join-Path $gradleCache 'org.hamcrest\hamcrest-core\1.3') -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1 -ExpandProperty FullName
$androidJar = 'D:\ProgramData\Android\AndroidSDK\platforms\android-36\android.jar'
$jsonJar = Get-ChildItem -LiteralPath (Join-Path $gradleCache 'org.json\json') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName

$mainDir = Join-Path $projectDir 'app\src\main\java\cn\yueji\balance'
$names = 'Ledger','LedgerStore','HistoryImport','FundSheet','SheetSchema','Xlsx','FundEngine','FundRules','FundTransactions','ChartScale','YearSummary','RecordQuery','FundCodec','FundClient','TradingCalendar','HolidayUpdater','AppLock','LabelRules','FlowOrder','NetworkIo','MonthLedger','FundRecognition','BankLedger','BankCodec','BankOcr','BankAccounting','FundDetailRecognition','ImportJournal','DebtLedger','FundNotice','MarketSession','PortfolioOrder','PortfolioSummary','AssetHistory','AssetSlices','AccountCash','InvestmentCash','RecurringInvestments','WatchAnalytics','WatchValidation','WatchOcr','FundProfile'
$sources = @($names | ForEach-Object { Join-Path $mainDir "$_.java" })
# FundNotifications pulls in the UI (MainActivity, R); the data layer only needs its publish() entry point.
$stubDir = Join-Path $compiledDir 'stub'
New-Item -ItemType Directory -Force -Path $stubDir | Out-Null
[System.IO.File]::WriteAllText((Join-Path $stubDir 'FundNotifications.java'), "package cn.yueji.balance;`npublic final class FundNotifications { public static void publish(android.content.Context c, Ledger.Data d) {} }`n", (New-Object System.Text.UTF8Encoding $false))
$sources += (Join-Path $stubDir 'FundNotifications.java')
$testDir = Join-Path $projectDir 'app\src\test\java\cn\yueji\balance'
$testFiles = Get-ChildItem -Path $testDir -Filter '*Test.java'
$tests = @($testFiles | ForEach-Object { $_.FullName }) + (Join-Path $testDir 'GenerateDataFiles.java')
& "$javaDir\javac.exe" '-J-Dfile.encoding=UTF-8' '-J-Dstdout.encoding=UTF-8' '-J-Dstderr.encoding=UTF-8' -encoding UTF-8 -nowarn -cp "$androidJar;$junitJar;$hamcrestJar" -d $compiledDir @sources @tests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Set-Location $projectDir
if ($Generate) {
    & "$javaDir\java.exe" '-Dfile.encoding=UTF-8' -cp "$compiledDir;$jsonJar;$androidJar" cn.yueji.balance.GenerateDataFiles docs
    exit $LASTEXITCODE
}
$classes = $testFiles | ForEach-Object { "cn.yueji.balance.$($_.BaseName)" }
& "$javaDir\java.exe" '-Dfile.encoding=UTF-8' -cp "$compiledDir;$jsonJar;$androidJar;$junitJar;$hamcrestJar" org.junit.runner.JUnitCore @classes
exit $LASTEXITCODE
