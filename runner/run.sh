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

# Compile when a class is missing OR older than the source it comes from.
# Checking only for a missing file is not enough: a failed edit leaves the
# previous class in place, and this script would then run code that no longer
# exists — a verification run passing against a build that never happened.
# The original applet's classes are compiled from ../Sources, so a re-encode
# or an edit there is caught the same way.
needs_compile() {
    for cls in JSApplet.class SearchThread.class JSearchRunner.class; do
        [ -f "classes/$cls" ] || return 0
    done
    [ JSearchRunner.java -nt classes/JSearchRunner.class ] && return 0
    [ ../Sources/JSApplet.java -nt classes/JSApplet.class ] && return 0
    [ ../Sources/SearchThread.java -nt classes/SearchThread.class ] && return 0
    return 1
}

if needs_compile; then
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
#
# Windows only, and deliberately so: that file names Windows CJK faces and
# describes a GDI-specific defect. Handing it to a Linux or macOS JVM would
# replace the platform's own font configuration with a mapping it cannot
# satisfy, so everywhere else AWT keeps its built-in behaviour. (CI runs on
# Linux and installs fonts-noto-cjk for glyph coverage instead.)
FONTCONFIG_FLAG=""
case "$(uname -s)" in
    CYGWIN*|MINGW*|MSYS*)
        if [ -f fontconfig.properties ]; then
            FONTCONFIG_FLAG="-Dsun.awt.fontconfig=$(cygpath -m "$PWD")/fontconfig.properties"
        fi
        ;;
esac
exec java -Dfile.encoding=UTF-8 $FONTCONFIG_FLAG -cp classes JSearchRunner "$@"
