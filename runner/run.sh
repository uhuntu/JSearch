#!/bin/sh
# Launch the untouched original JSearch 2.0.0.0 applet (Sources/) in a local
# frame window. A tiny server on 127.0.0.1:8901 plays the role of a search
# engine so a live search actually returns results. See README.md.
#
# Usage: ./run.sh [--go] [--snap=FILE]
#   --go         auto-run one search for "JSearch" after startup
#   --snap=FILE  save a PNG of the applet 6 seconds after startup
set -e
cd "$(dirname "$0")"

if [ ! -f classes/JSearchRunner.class ]; then
    mkdir -p classes
    javac -encoding GBK -d classes ../Sources/JSApplet.java ../Sources/SearchThread.java
    javac -encoding UTF-8 -nowarn -cp classes -d classes JSearchRunner.java
fi

if ! curl -s --noproxy '*' -o /dev/null http://127.0.0.1:8901/ 2>/dev/null; then
    python3 DemoServer.py &
    sleep 0.5
fi

exec java -cp classes JSearchRunner "$@"
