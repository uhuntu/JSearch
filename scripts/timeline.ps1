# Generate a timeline visualization of JSearch versions
# Outputs a text-based timeline

$versions = @(
    @{ Date="1999"; Event="Project starts"; Version=""; Lines="" },
    @{ Date="2000-08"; Event="v1.2.3 baseline"; Version="1.2.3"; Lines="1,512" },
    @{ Date="2001-12"; Event="Last large version"; Version="1.2.3"; Lines="1,529" },
    @{ Date="2002-01"; Event="Rewrite to v2.0.0.0"; Version="2.0.0.0"; Lines="906" },
    @{ Date="2002-03"; Event="Deadlock patch"; Version="2.0.0.0+"; Lines="906" },
    @{ Date="2015-21"; Event="Applets removed from browsers"; Version=""; Lines="" },
    @{ Date="2026-09"; Event="Archive recovered"; Version=""; Lines="" },
    @{ Date="2026-09"; Event="modern/ reference design"; Version=""; Lines="13 files" },
    @{ Date="2026-10"; Event="Infrastructure added"; Version=""; Lines="30+ files" }
)

Write-Host "JSearch Timeline" -ForegroundColor Cyan
Write-Host "================" -ForegroundColor Cyan
Write-Host ""

$maxLength = ($versions | ForEach-Object { $_.Date.Length } | Measure-Object -Maximum).Maximum

foreach ($v in $versions) {
    $date = $v.Date.PadRight($maxLength)
    $event = $v.Event
    
    if ($v.Version) {
        $version = " [$($v.Version)]"
    } else {
        $version = ""
    }
    
    if ($v.Lines) {
        $lines = " ($($v.Lines) lines)"
    } else {
        $lines = ""
    }
    
    Write-Host "$date │ $event$version$lines"
}

Write-Host ""
Write-Host "Key Events:" -ForegroundColor Yellow
Write-Host "  • 1999-2002: Active development by Hunt Lin"
Write-Host "  • 2002-2026: Dormant (applets obsolete)"
Write-Host "  • 2026: Archive recovery and modern reference design"
Write-Host ""
