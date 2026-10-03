#!/bin/bash
# JSearch modern/ build and test script
# Requires JDK 11 or later

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/out"
SRC_DIR="$SCRIPT_DIR/src"

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -t, --test       Run tests after building"
    echo "  -b, --benchmark  Run benchmarks after building"
    echo "  -c, --clean      Clean build output before building"
    echo "  -h, --help       Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                # Compile only"
    echo "  $0 -t             # Compile and run tests"
    echo "  $0 -b             # Compile and run benchmarks"
    echo "  $0 -c -t          # Clean, compile, and run tests"
}

CLEAN=false
TEST=false
BENCHMARK=false

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
find "$SRC_DIR" -name "*.java" > /tmp/jsearch_sources.txt
javac -encoding UTF-8 -d "$OUT_DIR" @/tmp/jsearch_sources.txt
rm /tmp/jsearch_sources.txt

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
