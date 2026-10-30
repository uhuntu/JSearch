#!/bin/sh
# Build the browser bundle for the CheerpJ host.
#
# CheerpJ 4.3 ships a Java 8 runtime (/8/jre/lib/rt.jar in its fetch log),
# so the classes the browser loads must be Java 8 bytecode: the desktop
# build in classes/ targets the JDK that compiled it (11) and CheerpJ's
# JVM rejects it with UnsupportedClassVersionError - the applet's main
# never runs and nothing at all appears on the page. Hence --release 8.
#
# Output: cheerpj-app/ with the applet classes, JSENGINES.TXT and (from
# index.html) the loader page. Runtime files under cheerpj-app/cheerpj/4.3/
# are self-hosted on first request by the bridge and are not built here.
set -e
cd "$(dirname "$0")"

rm -f cheerpj-app/JSApplet*.class cheerpj-app/SearchThread.class \
      cheerpj-app/ResultsDetails.class cheerpj-app/EnginesDetails.class

# The untouched 2002 sources, GBK, Java 8 bytecode.
javac --release 8 -encoding GBK -d cheerpj-app \
    ../Sources/JSApplet.java ../Sources/SearchThread.java

# The engine table, UTF-8 (the same copy the desktop runner serves).
cp JSENGINES.TXT cheerpj-app/JSENGINES.TXT

echo "browser bundle: $(ls cheerpj-app/*.class | wc -l) classes + JSENGINES.TXT"
