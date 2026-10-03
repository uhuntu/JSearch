# Release script for JSearch archive
# Creates a tagged release with changelog

param(
    [string]$Version,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if (-not $Version) {
    Write-Error "Usage: .\release.ps1 -Version <version>"
    Write-Error "Example: .\release.ps1 -Version 1.0.0"
    exit 1
}

Write-Host "Preparing release $Version..." -ForegroundColor Cyan

# Check for uncommitted changes
$status = git status --porcelain
if ($status) {
    Write-Error "You have uncommitted changes. Please commit or stash them first."
    exit 1
}

# Run tests
Write-Host "Running tests..." -ForegroundColor Yellow
Set-Location modern
.\build.ps1 -Test
if ($LASTEXITCODE -ne 0) {
    Write-Error "Tests failed. Cannot release."
    exit 1
}
Set-Location ..

# Update CHANGELOG
Write-Host "Updating CHANGELOG..." -ForegroundColor Yellow
$date = Get-Date -Format "yyyy-MM-dd"
$changelogContent = Get-Content CHANGELOG.md -Raw
$changelogContent = $changelogContent -replace "## \[Unreleased\]", "## [$Version] - $date`n`n## [Unreleased]"
Set-Content CHANGELOG.md -Value $changelogContent -NoNewline

# Commit changelog
git add CHANGELOG.md
git commit -m "release: prepare v$Version"

# Tag
if (-not $DryRun) {
    Write-Host "Creating tag v$Version..." -ForegroundColor Yellow
    git tag -a "v$Version" -m "Release v$Version"
    
    Write-Host ""
    Write-Host "Release $Version prepared!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "  git push origin main"
    Write-Host "  git push origin v$Version"
    Write-Host ""
    Write-Host "Then create a GitHub release from the tag."
} else {
    Write-Host ""
    Write-Host "Dry run complete. No tag created." -ForegroundColor Yellow
}
