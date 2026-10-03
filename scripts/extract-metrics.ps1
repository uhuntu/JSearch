# Extract metrics from the original JSearch source code
# Generates a report of code complexity and structure

param(
    [string]$SourcePath = "c:\Users\huntl\work\JSearch\Sources\JSApplet.java"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $SourcePath)) {
    Write-Error "Source file not found: $SourcePath"
    exit 1
}

$content = Get-Content $SourcePath -Raw -Encoding Default

# Count lines
$totalLines = ($content -split "`n").Count
$codeLines = ([regex]::Matches($content, '^\s*\S')).Count
$commentLines = ([regex]::Matches($content, '^\s*//')).Count
$blankLines = ([regex]::Matches($content, '^\s*$')).Count

# Count classes
$classes = ([regex]::Matches($content, '(?:public\s+)?class\s+\w+')).Count

# Count methods
$methods = ([regex]::Matches($content, '(?:public|private|protected|static)\s+\w+\s+\w+\s*\(')).Count

# Count inner classes
$innerClasses = ([regex]::Matches($content, 'class\s+\w+\s+implements')).Count

# Count static fields
$staticFields = ([regex]::Matches($content, 'static\s+\w+\s+\w+\s*[=;]')).Count

# Count synchronized blocks
$syncBlocks = ([regex]::Matches($content, 'synchronized\s*\(')).Count

# Count Thread usage
$threads = ([regex]::Matches($content, 'new\s+Thread\(')).Count

# Count Hashtable/Vector usage
$hashtables = ([regex]::Matches($content, 'Hashtable')).Count
$vectors = ([regex]::Matches($content, 'Vector')).Count

# Count setBounds calls (absolute positioning)
$setBounds = ([regex]::Matches($content, 'setBounds\(')).Count

# Count finalize overrides
$finalize = ([regex]::Matches($content, 'void\s+finalize\s*\(')).Count

Write-Host "JSearch Source Code Metrics" -ForegroundColor Cyan
Write-Host "==========================" -ForegroundColor Cyan
Write-Host ""
Write-Host "File: $SourcePath" -ForegroundColor Gray
Write-Host ""
Write-Host "Lines:" -ForegroundColor Yellow
Write-Host "  Total:      $totalLines"
Write-Host "  Code:       $codeLines"
Write-Host "  Comments:   $commentLines"
Write-Host "  Blank:      $blankLines"
Write-Host ""
Write-Host "Structure:" -ForegroundColor Yellow
Write-Host "  Classes:    $classes"
Write-Host "  Methods:    $methods"
Write-Host "  Inner:      $innerClasses"
Write-Host "  Static:     $staticFields"
Write-Host ""
Write-Host "Concurrency:" -ForegroundColor Yellow
Write-Host "  Threads:    $threads"
Write-Host "  Sync:       $syncBlocks"
Write-Host "  Finalize:   $finalize"
Write-Host ""
Write-Host "Data Structures:" -ForegroundColor Yellow
Write-Host "  Hashtable:  $hashtables"
Write-Host "  Vector:     $vectors"
Write-Host ""
Write-Host "UI:" -ForegroundColor Yellow
Write-Host "  setBounds:  $setBounds (absolute positioning)"
Write-Host ""

# Calculate metrics
$commentRatio = if ($totalLines -gt 0) { [math]::Round(($commentLines / $totalLines) * 100, 1) } else { 0 }
$methodComplexity = if ($methods -gt 0) { [math]::Round($totalLines / $methods, 1) } else { 0 }

Write-Host "Derived Metrics:" -ForegroundColor Yellow
Write-Host "  Comment ratio:    $commentRatio%"
Write-Host "  Avg method size:  $methodComplexity lines"
Write-Host ""

if ($setBounds -gt 50) {
    Write-Host "WARNING: High use of absolute positioning ($setBounds setBounds calls)" -ForegroundColor Red
}
if ($finalize -gt 5) {
    Write-Host "WARNING: Multiple finalize() overrides (forces GC)" -ForegroundColor Red
}
if ($threads -gt 3) {
    Write-Host "WARNING: Many manual threads ($threads Thread creations)" -ForegroundColor Yellow
}
