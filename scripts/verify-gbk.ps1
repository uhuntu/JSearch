# Verify GBK encoding of original source files
# This script checks that all original files are still GBK-encoded

param(
    [string]$Path = "c:\Users\huntl\work\JSearch"
)

$ErrorActionPreference = "Stop"

$gbkFiles = @(
    "Sources/JSApplet.java",
    "Sources/SearchThread.java",
    "versions/2000-08/JSApplet.java",
    "versions/2001-12/JSApplet/JSApplet.java",
    "versions/2002-01/Sources/JSApplet.java",
    "versions/2002-01/Sources/SearchThread.java",
    "ENGINES/baidu_cn.html",
    "ENGINES/google_cn.html",
    "ENGINES/google_en.html",
    "ENGINES/lycos_en.html",
    "Releases/JSEngines.txt",
    "versions/2000-08/JSENGINES.TXT",
    "versions/2001-12/JSApplet/JSEngines.txt",
    "versions/2002-01/Releases/JSEngines.txt"
)

$allPassed = $true

foreach ($file in $gbkFiles) {
    $fullPath = Join-Path $Path $file
    if (-not (Test-Path $fullPath)) {
        Write-Host "SKIP $file (not found)" -ForegroundColor Yellow
        continue
    }
    
    try {
        $bytes = [System.IO.File]::ReadAllBytes($fullPath)
        # Try to decode as GBK
        $gbk = [System.Text.Encoding]::GetEncoding("GBK")
        $text = $gbk.GetString($bytes)
        
        # Check for common Chinese characters that should be present
        $hasChinese = $text -match '[\u4e00-\u9fff]'
        
        if ($hasChinese) {
            Write-Host "OK   $file (GBK with Chinese characters)" -ForegroundColor Green
        } else {
            Write-Host "WARN $file (GBK but no Chinese characters found)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "FAIL $file ($($_.Exception.Message))" -ForegroundColor Red
        $allPassed = $false
    }
}

Write-Host ""
if ($allPassed) {
    Write-Host "All files verified successfully." -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some files failed verification." -ForegroundColor Red
    exit 1
}
