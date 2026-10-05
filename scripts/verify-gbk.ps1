# Verify that the archive's original files are still in the encoding they were
# preserved in, unchanged since 2002.
#
# The important thing about this check is that it decodes STRICTLY. Decoding GBK
# with .NET's default fallback replaces every unrecognised byte with U+FFFD
# instead of failing, and Chinese decodes to plausible-looking Chinese however
# you do it, so a lenient check passes a file that has already been rewritten as
# UTF-8. A file that is GBK is almost never valid UTF-8, so trying both decoders
# and seeing which one throws does distinguish them.
#
# This duplicates modern/src/test/java/jsearch/EncodingGuard.java, which is the
# version CI runs because it does not need Windows. Keep the two file lists in
# step; prefer the Java one when adding files.

param(
    [string]$Path = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

# Files that must still be GBK. ASCII counts as fine: nothing in such a file
# depends on the answer.
$gbkFiles = @(
    "Sources/JSApplet.java",
    "Sources/SearchThread.java",
    "Sources/codebase.dat",
    "ENGINES/baidu_cn.html",
    "ENGINES/engines.txt",
    "ENGINES/google_cn.html",
    "ENGINES/google_en.html",
    "Releases/COPYING.TXT",
    "Releases/CREDITS.TXT",
    "Releases/JSEngines.txt",
    "Releases/JSearch.html",
    "versions/2000-08/CODEBASE.DAT",
    "versions/2000-08/COPYING.TXT",
    "versions/2000-08/CREDITS.TXT",
    "versions/2000-08/JSApplet.java",
    "versions/2000-08/JSENGINES.TXT",
    "versions/2001-12/JSApplet/codebase.dat",
    "versions/2001-12/JSApplet/COPYING.TXT",
    "versions/2001-12/JSApplet/CREDITS.TXT",
    "versions/2001-12/JSApplet/JSApplet.java",
    "versions/2001-12/JSApplet/JSEngines.txt",
    "versions/2001-12/JSApplet/JSearch.htm",
    "versions/2002-01/Releases/COPYING.TXT",
    "versions/2002-01/Releases/CREDITS.TXT",
    "versions/2002-01/Releases/JSEngines.txt",
    "versions/2002-01/Releases/JSearch.html",
    "versions/2002-01/Sources/codebase.dat",
    "versions/2002-01/Sources/JSApplet.java",
    "versions/2002-01/Sources/SearchThread.java"
)

# Whole directories of GBK files. Every .txt under docs/txts is one of the eight
# GB8567-88 documents, and their names are Chinese, so they are globbed rather
# than written out: this script carries no non-ASCII of its own, and Windows
# PowerShell 5.1 mis-reads a UTF-8 script that has no byte-order mark.
$gbkDirs = @("docs/txts")

# The one original page that was never Chinese: ten bytes above 0x7F in 22,755,
# all Western European punctuation, so it is ISO-8859-1 rather than GBK.
$latin1Files = @(
    "ENGINES/lycos_en.html"
)

try {
    $gbk = [System.Text.Encoding]::GetEncoding("GBK",
        [System.Text.EncoderFallback]::ExceptionFallback,
        [System.Text.DecoderFallback]::ExceptionFallback)
} catch {
    Write-Host "GBK charset is not available on this machine; cannot verify." -ForegroundColor Yellow
    exit 0
}

$latin1 = [System.Text.Encoding]::GetEncoding("ISO-8859-1",
    [System.Text.EncoderFallback]::ExceptionFallback,
    [System.Text.DecoderFallback]::ExceptionFallback)
$utf8 = [System.Text.UTF8Encoding]::new($false, $true)

function Test-DecodesAs([byte[]]$bytes, [System.Text.Encoding]$encoding) {
    try {
        $null = $encoding.GetString($bytes)
        return $true
    } catch {
        return $false
    }
}

function Get-Kind([byte[]]$bytes) {
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        return "UTF-8+BOM"
    }
    if ($bytes.Length -eq 0 -or ($bytes | Where-Object { $_ -gt 0x7F }).Count -eq 0) {
        return "ASCII"
    }
    $isUtf8 = Test-DecodesAs $bytes $utf8
    $isGbk = Test-DecodesAs $bytes $gbk
    if ($isUtf8 -and $isGbk) {
        # Both decoders accept it: UTF-8 continuation bytes also look like GBK
        # trail bytes. GB2312 keeps every byte of a Chinese character at 0xA1 or
        # above, so a byte in 0x80-0xA0 is a continuation byte, and the file is
        # UTF-8; without one, GBK is the reading that makes sense of the text.
        $hasContinuationBytes = $false
        foreach ($b in $bytes) {
            if ($b -ge 0x80 -and $b -le 0xA0) { $hasContinuationBytes = $true; break }
        }
        if ($hasContinuationBytes) { return "UTF-8" }
        return "GBK"
    }
    if ($isUtf8) { return "UTF-8" }
    if ($isGbk) { return "GBK" }
    if (Test-DecodesAs $bytes $latin1) { return "ISO-8859-1" }
    return "undecodable"
}

Write-Host "Verifying $Path"
Write-Host ""

$toCheck = @($gbkFiles)
foreach ($dir in $gbkDirs) {
    $resolved = Join-Path $Path $dir
    if (Test-Path $resolved -PathType Container) {
        $toCheck += (Get-ChildItem $resolved -Filter *.txt | ForEach-Object { "$dir/$($_.Name)" })
    }
}

$failures = @()
$counts = @{}

foreach ($group in @(@{ Expect = "GBK"; Files = $toCheck }, @{ Expect = "ISO-8859-1"; Files = $latin1Files })) {
    $expect = $group.Expect
    foreach ($rel in $group.Files) {
        $target = Join-Path $Path $rel
        if (-not (Test-Path $target -PathType Leaf)) {
            Write-Host "  missing  $rel" -ForegroundColor Red
            $failures += "$rel expected $expect but is missing"
            continue
        }

        $bytes = [System.IO.File]::ReadAllBytes($target)
        $kind = Get-Kind $bytes
        $counts[$kind] = ($counts[$kind] + 1)

        # ASCII has no bytes that depend on the answer.
        $ok = $kind -eq $expect -or $kind -eq "ASCII"
        if ($ok) {
            Write-Host ("  {0,-12} {1}" -f $kind, $rel) -ForegroundColor Green
        } else {
            Write-Host ("  {0,-12} {1} (expected {2})" -f $kind, $rel, $expect) -ForegroundColor Red
            $failures += "$rel expected $expect but is $kind - the bytes were rewritten, not merely misread on screen"
        }
    }
}

Write-Host ""
if ($failures.Count -eq 0) {
    $summary = ($counts.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Value) $($_.Name)" }) -join ", "
    Write-Host "All $($toCheck.Count + $latin1Files.Count) original files verified ($summary)." -ForegroundColor Green
    exit 0
} else {
    Write-Host "$($failures.Count) file(s) are no longer the encoding they were preserved in:" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
