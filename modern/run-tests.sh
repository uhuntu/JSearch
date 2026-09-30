#!/bin/sh
# Compiles main + test sources and runs the dependency-free test suite.
set -e
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" $(find src -name '*.java')
java -Dfile.encoding=UTF-8 -cp "$out" jsearch.Tests
