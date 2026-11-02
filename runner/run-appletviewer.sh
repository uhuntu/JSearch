#!/bin/sh
# Run the archived 2002 applet in the JDK 8 appletviewer - the way it was
# actually used: one JDK tool, one HTML page, an <applet> tag. The runner
# (JSearchRunner) answers "does the original still run under a modern stub?";
# this answers "what did using it feel like". Same applet, same engine table,
# Sun's own stub instead of ours.
#
# Needs a JDK 8 (appletviewer was removed in JDK 11) and the demo server,
# which this starts if it is not already listening, exactly like run.sh.
#
# Usage: ./run-appletviewer.sh
set -e
cd "$(dirname "$0")"

WORK=appletviewer-work
mkdir -p "$WORK/classes"

# Locate an appletviewer and its JDK home. First the PATH, then the usual
# Debian/Ubuntu JVM directory. The viewer runs on the JVM it ships with, so
# the classes must be Java 8 bytecode: classes/ is built by whatever JDK
# compiled it last, so this route keeps its own compiled copy.
AV=$(command -v appletviewer || true)
if [ -z "$AV" ]; then
    for d in /usr/lib/jvm/java-8-openjdk-amd64 /usr/lib/jvm/*java-8*; do
        if [ -x "$d/bin/appletviewer" ]; then AV="$d/bin/appletviewer"; break; fi
    done
fi
if [ -z "$AV" ]; then
    echo "no appletviewer found - install a JDK 8 (e.g. sudo apt install openjdk-8-jdk)" >&2
    exit 1
fi
JVM_HOME=$(dirname "$(dirname "$(readlink -f "$AV")")")
JAVAC8="$JVM_HOME/bin/javac"

# Rebuild the Java 8 copy when stale (the same staleness rule run.sh uses).
if [ ! -f "$WORK/classes/JSApplet.class" ] \
   || [ ../Sources/JSApplet.java -nt "$WORK/classes/JSApplet.class" ] \
   || [ ../Sources/SearchThread.java -nt "$WORK/classes/SearchThread.class" ]; then
    "$JAVAC8" -encoding GBK -d "$WORK/classes" \
        ../Sources/JSApplet.java ../Sources/SearchThread.java
fi

# The engine table lives in this directory (UTF-8 conversion of the shipped
# Releases/JSEngines.txt plus the LocalDemo/SearXNG/DeepSeek records), the
# same copy the desktop runner serves. currUrl points here, file: scheme.
printf '<html>\n<head><title>JSearch 2.0.0.0 - appletviewer</title></head>\n<body>\n<applet code="JSApplet.class"\n        codebase="file:%s/%s/classes/"\n        width=758 height=403>\n  <param name="currUrl"  value="file:%s/">\n  <param name="_readTxt" value="No">\n</applet>\n</body>\n</html>\n' \
    "$PWD" "$WORK" "$PWD" > "$WORK/JSearch.html"

# An unsigned applet may not open sockets across origins; the demo engine is
# http://localhost:8901 while the codebase is file:, so grant everything for
# this local session.
printf 'grant {\n    permission java.security.AllPermission;\n};\n' > "$WORK/all.policy"

if ! curl -s --noproxy '*' -o /dev/null http://127.0.0.1:8901/ 2>/dev/null; then
    python3 DemoServer.py &
    sleep 0.5
fi

# -Dfile.encoding=UTF-8: the engine table is UTF-8 and the applet reads it
# with the platform charset (see run.sh for the same note). Everything here
# is per-user, regenerable state - appletviewer-work/ is gitignored.
exec "$AV" -J-Dfile.encoding=UTF-8 \
    -J-Djava.security.policy="$PWD/$WORK/all.policy" \
    "$WORK/JSearch.html"
