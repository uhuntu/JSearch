#!/bin/bash
# JSearch modern/ build and test script
# Requires JDK 11 or later

set -e

# Pin a UTF-8 locale so the JVM derives sun.jnu.encoding (the filename
# encoding) as UTF-8 regardless of the host locale. The test suite enumerates
# GBK-named files such as docs/txts/1.*.txt, which throw InvalidPathException
# when sun.jnu.encoding is ASCII (e.g. a C/POSIX locale). -Dfile.encoding only
# fixes content decoding, not filenames, so the locale must be set here.
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/out"
SRC_DIR="$SCRIPT_DIR/src"

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -t, --test       Run tests after building"
    echo "  -b, --benchmark  Run benchmarks after building"
    echo "  -w, --web        Launch interactive Web UI and REST API server"
    echo "  -p, --port PORT  Port for Web UI (default: 8080)"
    echo "  -c, --clean      Clean build output before building"
    echo "  -h, --help       Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                # Compile only"
    echo "  $0 -t             # Compile and run tests"
    echo "  $0 -b             # Compile and run benchmarks"
    echo "  $0 -w             # Launch interactive Web UI"
    echo "  $0 -c -t          # Clean, compile, and run tests"
}

CLEAN=false
TEST=false
BENCHMARK=false
WEB=false
PORT=8080

while [[ $# -gt 0 ]]; do
    case $1 in
        -t|--test)
            TEST=true
            shift
            ;;
        -b|--benchmark)
            BENCHMARK=true
            shift
            ;;
        -w|--web)
            WEB=true
            shift
            ;;
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        -c|--clean)
            CLEAN=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

if [ "$CLEAN" = true ] && [ -d "$OUT_DIR" ]; then
    echo "Cleaning $OUT_DIR..."
    rm -rf "$OUT_DIR"
fi

if [ ! -d "$OUT_DIR" ]; then
    mkdir -p "$OUT_DIR"
fi

echo "Compiling..."
SOURCES_LIST="$(mktemp)"
trap 'rm -f "$SOURCES_LIST"' EXIT
find "$SRC_DIR" -name "*.java" > "$SOURCES_LIST"
javac -encoding UTF-8 -d "$OUT_DIR" @"$SOURCES_LIST"

echo "Compilation successful."

if [ "$TEST" = true ]; then
    echo ""
    echo "Running tests..."
    java -Dfile.encoding=UTF-8 -cp "$OUT_DIR" jsearch.Tests
fi

if [ "$BENCHMARK" = true ]; then
    echo ""
    echo "Running benchmarks..."
    java -Dfile.encoding=UTF-8 -cp "$OUT_DIR" jsearch.Benchmarks
fi

if [ "$WEB" = true ]; then
    echo ""
    echo "Launching Web UI on http://localhost:$PORT..."
    java -Dfile.encoding=UTF-8 -cp "$OUT_DIR" jsearch.WebServer --port "$PORT"
fi
