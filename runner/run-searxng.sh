#!/bin/sh
# Opt-in: let the untouched 2002 applet search the live web through SearXNG.
#
# Starts the dialect bridge on 127.0.0.1:8902 if it is not already running,
# then launches the applet exactly as run.sh does. The SearXNG engine record
# the bridge answers for ships in JSENGINES.TXT; this script adds nothing to
# the tracked tree.
#
# Requires: python3, and a local SearXNG with its JSON API enabled on
# http://127.0.0.1:8888 (see SearxngBridge.py). Without the bridge the record
# simply fails like the historical engines do — selecting it shows the
# original's own error handling.
#
# Usage: ./run-searxng.sh [--go] [--snap=FILE]
set -e
cd "$(dirname "$0")"

if ! curl -s --noproxy '*' -o /dev/null http://127.0.0.1:8902/ 2>/dev/null; then
    nohup python3 SearxngBridge.py > /tmp/searxng-bridge.log 2>&1 &
    sleep 0.5
fi

exec ./run.sh "$@"
