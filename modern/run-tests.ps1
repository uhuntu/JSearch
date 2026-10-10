# Compiles main + test sources and runs the dependency-free test suite.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root 'out'
New-Item -ItemType Directory -Force $out | Out-Null
$sources = Get-ChildItem -Path (Join-Path $root 'src') -Recurse -Filter '*.java' |
           ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java '-Dsun.jnu.encoding=UTF-8' '-Dfile.encoding=UTF-8' -cp $out jsearch.Tests
