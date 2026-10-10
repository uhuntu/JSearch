#!/bin/sh
# Compiles main + test sources and runs the dependency-free test suite.
set -e
# Pin a UTF-8 locale so the JVM's sun.jnu.encoding (filename encoding) is UTF-8
# regardless of the host locale; the suite enumerates GBK-named files that throw
# InvalidPathException under an ASCII locale (e.g. C/POSIX).
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" $(find src -name '*.java')
java -Dfile.encoding=UTF-8 -cp "$out" jsearch.Tests
