@echo off
rem === JSearXNG 2002 Applet, one click.
rem === Wakes the WSL SearXNG, keeps it alive, starts the dialect bridge
rem === (DEEPSEEK_API_KEY comes from the user's registry - set once with
rem === setx, never stored in this file) and opens the untouched applet
rem === with the DeepSeek engine selected.

for /f "tokens=2,*" %%A in ('reg query HKCU\Environment /v DEEPSEEK_API_KEY 2^>nul') do set "DEEPSEEK_API_KEY=%%B"

rem WSL loopback cannot wake a stopped distro from the Java side, so the
rem distro is woken here and held open for the session.
wsl -d Ubuntu -e true
start "searxng-keepalive" /min wsl -d Ubuntu -e sleep infinity

set "BASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%BASH%" set "BASH=C:\Program Files\Git\bin\bash.exe"
if not exist "%BASH%" set "BASH=bash"

start "jsearch-applet" /min "%BASH%" -c "cd '%~dp0' && ./run-searxng.sh --engine=DeepSeek"
