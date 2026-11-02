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

# Fonts for CheerpJ's Linux fontconfig stack. It scans the runtime's
# fc/ttf/ directory (enumerated through a sibling index.list, because the
# browser cannot list directories over HTTP); with no fonts in it the font
# manager dies with "failed to initialize fontconfig" on the first text
# draw and the applet paints nothing but a white rectangle. The official
# CDN ships no fc/ payload for 4.3/5.0, so populate it from the host:
# DejaVu for the Latin faces, Noto Sans CJK SC for the Chinese UI.
# CheerpJ 5.0 is what the page loads (index.html); under self-hosted 4.3
# even a fonted fontconfig left the applet white, while 5.0 renders both
# this applet and a control applet (FoilSim) correctly.
for ver in 5.0; do
    fcd="cheerpj-app/cheerpj/$ver/fc/ttf"
    mkdir -p "$fcd"
    cp /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf \
       /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf \
       /usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf "$fcd/"
    if [ ! -s "$fcd/NotoSansCJKsc-Regular.otf" ]; then
        # github.com is reachable without the corporate proxy here; the
        # CDN host that serves the runtime itself is only reachable
        # through it, and the proxy answers these font URLs with 0 bytes.
        curl -sL --noproxy '*' --max-time 300 \
            -o "$fcd/NotoSansCJKsc-Regular.otf" \
            'https://github.com/notofonts/noto-cjk/raw/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf' \
        || rm -f "$fcd/NotoSansCJKsc-Regular.otf"
    fi
    find "$fcd" -maxdepth 1 -type f ! -name index.list -printf '%f\n' | sort \
        > "$fcd/index.list"
done

echo "browser bundle: $(ls cheerpj-app/*.class | wc -l) classes + JSENGINES.TXT"
