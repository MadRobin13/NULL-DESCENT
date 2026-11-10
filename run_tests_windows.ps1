# Native Windows test runner for NULL//DESCENT.
# Run from the repo root: powershell -ExecutionPolicy Bypass -File .\run_tests_windows.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK javac not found in PowerShell PATH.' }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java runtime not found in PowerShell PATH.' }

$library = Join-Path $PSScriptRoot 'lib'
$junit = Join-Path $library 'junit-platform-console-standalone-1.10.0.jar'
$jline = Join-Path $library 'jline.jar'
if (-not (Test-Path $jline)) { throw "Missing JLine dependency: $jline" }
if (-not (Test-Path $junit)) {
    Write-Host 'Downloading JUnit 5 standalone console...'
    New-Item -ItemType Directory -Force $library | Out-Null
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar' -OutFile $junit
}

$build = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $build 'classes'
$testClasses = Join-Path $build 'test-classes'
if (Test-Path $classes) { Remove-Item $classes -Force -Recurse }
if (Test-Path $testClasses) { Remove-Item $testClasses -Force -Recurse }
New-Item -ItemType Directory -Force -Path @($classes, $testClasses) | Out-Null

$sources = @(Get-ChildItem -Path (Join-Path $PSScriptRoot 'src') -Filter '*.java' -Recurse -File | ForEach-Object FullName)
$tests = @(Get-ChildItem -Path (Join-Path $PSScriptRoot 'test') -Filter '*.java' -Recurse -File | ForEach-Object FullName)
if ($sources.Count -eq 0 -or $tests.Count -eq 0) { throw 'Could not find Java source files or tests.' }

Write-Host 'Compiling production code (Java 17)...'
& javac --release 17 -cp "$library\*" -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Production compilation failed.' }

Write-Host 'Compiling JUnit tests (Java 17)...'
& javac --release 17 -cp "$classes;$library\*" -d $testClasses @tests
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }

Write-Host 'Running JUnit 5 tests...'
& java -jar $junit execute --class-path "$classes;$testClasses;$jline" --scan-class-path $testClasses --fail-if-no-tests
if ($LASTEXITCODE -ne 0) { throw 'JUnit reported failures.' }
Write-Host 'JUnit suite completed successfully.'
