@echo off
rem === JSearch in the browser, one click.
rem === Wakes the WSL SearXNG, holds the distro open, rebuilds the browser
rem === bundle if needed, starts the dialect bridge (the page's same-origin
rem === backend), and opens the CheerpJ page - the 2002 applet runs inside
rem === the browser tab. DEEPSEEK_API_KEY comes from the registry (set once
rem === with setx), never stored here.

for /f "tokens=2,*" %%A in ('reg query HKCU\Environment /v DEEPSEEK_API_KEY 2^>nul') do set "DEEPSEEK_API_KEY=%%B"

rem WSL loopback cannot wake a stopped distro from the Java side, so the
rem distro is woken here and held open for the session.
wsl -d Ubuntu -e true
start "searxng-keepalive" /min wsl -d Ubuntu -e sleep infinity

cd /d "%~dp0"

rem The browser bundle: the untouched sources compiled to Java 8 bytecode
rem (CheerpJ 4.3 ships a Java 8 runtime) plus the engine table. Fast, and
rem keeps cheerpj-app/ honest after any Sources change.
set "BASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%BASH%" set "BASH=C:\Program Files\Git\bin\bash.exe"
if not exist "%BASH%" set "BASH=bash"
"%BASH%" -c "cd '%~dp0' && ./build-cheerpj.sh"

rem The bridge serves the page, the classes, the engine table, the jar and
rem (self-hosted) the CheerpJ runtime on 127.0.0.1:8902. python, not the
rem python3.exe WindowsApps stub that fails to launch.
start "jsearxng-bridge" /min cmd /c "python SearxngBridge.py"
timeout /t 2 >nul
start "" http://127.0.0.1:8902/app/
