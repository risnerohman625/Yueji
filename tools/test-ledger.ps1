$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$javaDir = 'D:\ProgramData\Android\AndroidStudio\jbr\bin'
$compiledDir = Join-Path $projectDir '.tools\ledger-tests'
$resolved=[IO.Path]::GetFullPath($compiledDir);if(-not $resolved.StartsWith([IO.Path]::GetFullPath($projectDir)+[IO.Path]::DirectorySeparatorChar)){throw "Unsafe test output directory"}
if (Test-Path -LiteralPath $compiledDir) { Remove-Item -LiteralPath $compiledDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $compiledDir | Out-Null
$gradleCache = Join-Path $projectDir 'gradle-home\caches\modules-2\files-2.1'
$junitJar = Get-ChildItem -LiteralPath (Join-Path $gradleCache 'junit\junit\4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1 -ExpandProperty FullName
$hamcrestJar = Get-ChildItem -LiteralPath (Join-Path $gradleCache 'org.hamcrest\hamcrest-core\1.3') -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1 -ExpandProperty FullName
$androidJar = 'D:\ProgramData\Android\AndroidSDK\platforms\android-36\android.jar'
$jsonJar = Get-ChildItem -LiteralPath (Join-Path $gradleCache 'org.json\json') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$dependencyDir=Join-Path $compiledDir 'dependencies'
New-Item -ItemType Directory -Force -Path $dependencyDir | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$dependencyJars=@(Get-ChildItem -LiteralPath $gradleCache -Recurse -Filter '*.jar' | ForEach-Object {$_.FullName})
$index=0
foreach($aar in Get-ChildItem -LiteralPath $gradleCache -Recurse -Filter '*.aar'){
    $zip=[IO.Compression.ZipFile]::OpenRead($aar.FullName)
    try{
        $entry=$zip.GetEntry('classes.jar')
        if($entry){$target=Join-Path $dependencyDir "$index.jar";[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$true);$dependencyJars+=$target;$index++}
    }finally{$zip.Dispose()}
}
$compileClasspath="$androidJar;$junitJar;$hamcrestJar;"+($dependencyJars -join ';')

# Every main source compiles against android.jar; only the generated R class is replaced by a stub of the drawable names.
$stubDir = Join-Path $compiledDir 'stub'
New-Item -ItemType Directory -Force -Path $stubDir | Out-Null
$groups=@{}
foreach($kind in @('drawable','mipmap','layout')){
    $names=@(Get-ChildItem -LiteralPath (Join-Path $projectDir 'app\src\main\res') -Recurse -File | Where-Object { $_.Directory.Name -like "$kind*" } | ForEach-Object {$_.BaseName} | Sort-Object -Unique)
    $groups[$kind]=$names
}
$ids=@(Get-ChildItem -LiteralPath (Join-Path $projectDir 'app\src\main\res') -Recurse -Filter '*.xml' | ForEach-Object { [regex]::Matches([IO.File]::ReadAllText($_.FullName),'@\+id/(\w+)') | ForEach-Object {$_.Groups[1].Value} } | Sort-Object -Unique)
$groups['id']=$ids
$rText="package cn.yueji.balance;`npublic final class R {`n"
foreach($kind in $groups.Keys){$rText+=" public static final class $kind {`n";foreach($name in $groups[$kind]){$rText+="  public static final int $name = 0;`n"};$rText+=" }`n"}
$rText+="}`n"
[System.IO.File]::WriteAllText((Join-Path $stubDir 'R.java'), $rText, (New-Object System.Text.UTF8Encoding $false))

$sources = @(Get-ChildItem -Path (Join-Path $projectDir 'app\src\main\java\cn\yueji\balance') -Filter '*.java' | ForEach-Object { $_.FullName }) + (Join-Path $stubDir 'R.java')
$testFiles = Get-ChildItem -Path (Join-Path $projectDir 'app\src\test\java\cn\yueji\balance') -Filter '*Test.java'
$tests = $testFiles | ForEach-Object { $_.FullName }
$compilerArgs=@('-encoding','UTF-8','-nowarn','-cp',('"'+$compileClasspath.Replace('\','/')+'"'),'-d',('"'+$compiledDir.Replace('\','/')+'"'))+@(($sources+$tests) | ForEach-Object {'"'+$_.Replace('\','/')+'"'})
$argumentFile=Join-Path $compiledDir 'javac.args'
[IO.File]::WriteAllLines($argumentFile,[string[]]$compilerArgs,[Text.UTF8Encoding]::new($false))
& "$javaDir\javac.exe" '-J-Dfile.encoding=UTF-8' '-J-Dstdout.encoding=UTF-8' '-J-Dstderr.encoding=UTF-8' "@$argumentFile"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$classes = $testFiles | ForEach-Object { "cn.yueji.balance.$($_.BaseName)" }
& "$javaDir\java.exe" '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp "$compiledDir;$jsonJar;$androidJar;$junitJar;$hamcrestJar" org.junit.runner.JUnitCore @classes
exit $LASTEXITCODE
