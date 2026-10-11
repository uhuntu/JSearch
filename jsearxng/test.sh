#!/bin/sh
# Compile the app and its test suite together, then run the suite.
#
# Every check is on a pure function — the entity layer, query parsing, URL
# dedup keys, the poison check, Bing redirect unwrapping, escaping, the JSON
# reader, the page render — so this needs no network, no fixtures and no
# clock. The network half is the live service itself, plus each engine's own
# error reporting in the >>消息 pane.
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
