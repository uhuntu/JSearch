# JSearch modern/ build and test script
# Requires JDK 11 or later

param(
    [switch]$Test,
    [switch]$Benchmark,
    [switch]$Web,
    [int]$Port = 8080,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ModernRoot = $PSScriptRoot
$OutDir = Join-Path $ModernRoot "out"
$SrcDir = Join-Path $ModernRoot "src"

if ($Clean -and (Test-Path $OutDir)) {
    Write-Host "Cleaning $OutDir..."
    Remove-Item -Recurse -Force $OutDir
}

if (-not (Test-Path $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir | Out-Null
}

Write-Host "Compiling..."
$javaFiles = Get-ChildItem -Recurse -Path $SrcDir -Filter "*.java" | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -d $OutDir $javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed"
    exit 1
}

Write-Host "Compilation successful."

if ($Test) {
    Write-Host "`nRunning tests..."
    & java "-Dsun.jnu.encoding=UTF-8" "-Dfile.encoding=UTF-8" -cp $OutDir jsearch.Tests
    exit $LASTEXITCODE
}

if ($Benchmark) {
    Write-Host "`nRunning benchmarks..."
    & java "-Dsun.jnu.encoding=UTF-8" "-Dfile.encoding=UTF-8" -cp $OutDir jsearch.Benchmarks
    exit $LASTEXITCODE
}

if ($Web) {
    Write-Host "`nLaunching Web UI on http://localhost:$Port..."
    & java "-Dsun.jnu.encoding=UTF-8" "-Dfile.encoding=UTF-8" -cp $OutDir jsearch.WebServer --port $Port
    exit $LASTEXITCODE
}
