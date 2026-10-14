#!/bin/sh
# Compile the app and its test suite together, then run the suite.
#
# Pure result-processing checks plus injected-engine scheduling tests.
# No external network or service is needed; latches exercise concurrency,
# deadline expiry, queue saturation and cancellation.
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
# --release 16: the code uses records, instanceof patterns and text blocks, so
# 16 is the floor. Passing it means an older JDK fails here with "release version
# not supported" instead of a hundred parse errors inside the HTML template —
# which is what happened when this suite first ran against CI's JDK 11.
javac --release 16 -encoding UTF-8 -d out JSearXNG.java JSearXNGTest.java
java -cp out JSearXNGTest
