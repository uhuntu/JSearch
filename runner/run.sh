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

# -Dfile.encoding=UTF-8: the shim's JSENGINES.TXT and DemoServer's results
# page are UTF-8, but the applet reads with the platform charset — on a
# Chinese Windows JVM that default is GBK and every Chinese string in the
# engine table, result list and preview decodes as mojibake. (JDK 18+ no
# longer needs this; it defaults to UTF-8 everywhere.)
# -Dsun.awt.fontconfig: on Windows the AWT peers build their GDI font from
# the logical font's alphabetic component, which no longer contains CJK
# glyphs, so the UI draws boxes even when the strings are correct. The
# bundled fontconfig.properties re-points that component at a CJK face.
FONTCONFIG_FLAG=""
if [ -f fontconfig.properties ]; then
    case "$(uname -s)" in
        CYGWIN*|MINGW*|MSYS*) FONTCONFIG_FLAG="-Dsun.awt.fontconfig=$(cygpath -m "$PWD")/fontconfig.properties" ;;
        *) FONTCONFIG_FLAG="-Dsun.awt.fontconfig=$PWD/fontconfig.properties" ;;
    esac
fi
exec java -Dfile.encoding=UTF-8 $FONTCONFIG_FLAG -cp classes JSearchRunner "$@"
