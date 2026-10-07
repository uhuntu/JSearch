# Launch the untouched original JSearch 2.0.0.0 applet (Sources/) in a local
# frame window. A tiny server on 127.0.0.1:8901 plays the role of a search
# engine so a live search actually returns results. See README.md.
#
# PowerShell twin of run.sh, for running without Git Bash.
#
# Usage: .\run.ps1 [--go] [--snap=FILE]
#   --go         auto-run one search for "JSearch" after startup
#   --snap=FILE  save a PNG of the applet 6 seconds after startup
# (If your execution policy blocks scripts: powershell -ExecutionPolicy
#  Bypass -File run.ps1 --go)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path classes/JSearchRunner.class)) {
    New-Item -ItemType Directory -Force classes | Out-Null
    javac -encoding GBK -d classes ../Sources/JSApplet.java ../Sources/SearchThread.java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    javac -encoding UTF-8 -nowarn -cp classes -d classes JSearchRunner.java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Any HTTP answer (or an open port) means the demo server is already up.
$serverUp = $false
try {
    $client = New-Object Net.Sockets.TcpClient
    $client.Connect('127.0.0.1', 8901)
    $serverUp = $client.Connected
    $client.Dispose()
} catch { }

if (-not $serverUp) {
    # python3.exe on Windows is often the WindowsApps store-redirect stub
    # ("not a valid Win32 application" when launched); prefer python.exe and
    # skip anything under WindowsApps.
    $python = $null
    foreach ($name in 'python.exe', 'python3.exe') {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd -and $cmd.Source -and $cmd.Source -notlike '*WindowsApps*') {
            $python = $cmd.Source; break
        }
    }
    if ($python) {
        Start-Process $python -ArgumentList 'DemoServer.py' -NoNewWindow
        Start-Sleep -Milliseconds 500
    } else {
        Write-Warning 'no usable python on PATH; start DemoServer.py manually, then rerun'
    }
}

# Same flags as run.sh (see README.md for why each one exists).
$fontconfigFlag = ''
if (Test-Path fontconfig.properties) {
    $fontconfigFlag = "-Dsun.awt.fontconfig=$PWD/fontconfig.properties"
}

# Quote the -D args: PowerShell 5.1 splits unquoted arguments at the dot
# (-Dfile.encoding=UTF-8 would reach java as "-Dfile" + ".encoding=UTF-8").
# An empty $fontconfigFlag simply passes nothing.
& java "-Dfile.encoding=UTF-8" "$fontconfigFlag" -cp classes JSearchRunner @args
exit $LASTEXITCODE
