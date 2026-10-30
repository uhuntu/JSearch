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

rem The browser bundle: classes (built by run.sh) packed into a jar the
rem page's cheerpjRunJar loads. Rebuilt only when missing.
set "JAR=%ProgramFiles%\Java\jdk-11\bin\jar.exe"
if exist classes\JSearchRunner.class if not exist cheerpj-app\JSearch2002.jar (
    "%JAR%" --create --file cheerpj-app\JSearch2002.jar --main-class JSearchRunner -C classes .
)

rem The bridge serves the page, the classes, the engine table, the jar and
rem (self-hosted) the CheerpJ runtime on 127.0.0.1:8902. python, not the
rem python3.exe WindowsApps stub that fails to launch.
start "jsearxng-bridge" /min cmd /c "python SearxngBridge.py"
timeout /t 2 >nul
start "" http://127.0.0.1:8902/app/
