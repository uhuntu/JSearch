@echo off
setlocal

if not exist out (
    mkdir out
    echo Compiling...
    for /r "src\main\java" %%f in (*.java) do javac -encoding UTF-8 -d out "%%f" || goto :error
    for /r "src\test\java" %%f in (*.java) do javac -encoding UTF-8 -d out "%%f" || goto :error
)

echo Running tests...
java -Dfile.encoding=UTF-8 -cp out jsearch.Tests
exit /b %errorlevel%

:error
echo Build failed.
exit /b 1