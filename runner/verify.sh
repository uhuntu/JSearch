#!/bin/sh
# Assert the evidence trail that `run.sh --go` prints.
#
# CI runs this. It exists because runner/ is the only path in the repository
# that executes the original 2002 artifact, and until now the only evidence it
# still worked was a screenshot and a paragraph of prose. A future JDK, a
# re-encode of Sources/, or a stray edit to the engine file would break the
# applet silently; this fails instead.
#
# Every assertion is on ASCII text the runner prints verbatim, so the check
# never depends on the Chinese strings decoding a particular way in the CI log.
# What each assertion protects is named in its comment.
#
# Usage: ./verify.sh [LOGFILE]           check an existing run.sh --go log
#        ./verify.sh --live [LOGFILE]    run the applet against a fixture
#                                        SearXNG through the bridge, then check
#        ./verify.sh --combined [LOGFILE]  the same, with the demo engine as
#                                        well, so cross-engine dedup runs too
#        ./verify.sh --help
#
# --live and --combined need no SearXNG and no network: they start
# StubSearxng.py, point a bridge at it, and run the applet against a copy of
# the engine table whose SearXNG record names that bridge. The ports are
# chosen free and the copy is temporary, so neither disturbs a bridge already
# running for real. The applet opens a window, so the run is capped and
# timeout's exit 124 is expected; the log is the artefact and the assertions
# are the gate.
#
# Exits 0 if the trail is complete, 1 otherwise. All checks run, so one run
# reports every failure rather than only the first.

MODE=check
case "$1" in
    --live)     MODE=live;     shift ;;
    --combined) MODE=combined; shift ;;
    -h|--help)
        cat <<'EOF'
Assert the evidence trail that run.sh --go prints.

  ./verify.sh [LOGFILE]             check an existing run.sh --go log
  ./verify.sh --live [LOGFILE]      run the applet against a fixture SearXNG
                                    through the bridge, then check the trail
  ./verify.sh --combined [LOGFILE]  the same, with the demo engine as well, so
                                    cross-engine dedup is exercised too

--live and --combined need no SearXNG and no network: they start
StubSearxng.py, point a bridge at it, and run the applet against a temporary
copy of the engine table whose SearXNG record names that bridge. The applet
opens a window, so the run is capped and timeout's exit 124 is expected.

Exits 0 if the trail is complete, 1 otherwise.
EOF
        exit 0 ;;
esac

FAILURES=0
CHECKS=0

expect() {
    CHECKS=$((CHECKS + 1))
    if grep -qF -- "$1" "$LOG"; then
        echo "  ok    $2"
    else
        echo "  FAIL  $2"
        echo "        expected to find: $1"
        FAILURES=$((FAILURES + 1))
    fi
}

refute() {
    CHECKS=$((CHECKS + 1))
    if grep -qF -- "$1" "$LOG"; then
        echo "  FAIL  $2"
        echo "        should not appear: $1"
        FAILURES=$((FAILURES + 1))
    else
        echo "  ok    $2"
    fi
}

count_is() {
    CHECKS=$((CHECKS + 1))
    ACTUAL=$(grep -c -- "$1" "$LOG")
    if [ "$ACTUAL" = "$2" ]; then
        echo "  ok    $3 (found $ACTUAL)"
    else
        echo "  FAIL  $3 — expected $2, found $ACTUAL"
        FAILURES=$((FAILURES + 1))
    fi
}

# count_is takes a pattern; this takes a literal, for strings whose regex
# metacharacters ('  [selected]' is a bracket expression unescaped) would
# otherwise mean something else.
count_fixed() {
    CHECKS=$((CHECKS + 1))
    ACTUAL=$(grep -cF -- "$1" "$LOG")
    if [ "$ACTUAL" = "$2" ]; then
        echo "  ok    $3 (found $ACTUAL)"
    else
        echo "  FAIL  $3 — expected $2, found $ACTUAL"
        FAILURES=$((FAILURES + 1))
    fi
}

# ---------------------------------------------------------------- the fixture

# A port the OS says is free. There is a race between this and the bind, but
# the window is microseconds and the loser is a loud bind failure, not a
# silently wrong port.
free_port() {
    python3 -c 'import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()'
}

listening() {
    curl -s --noproxy '*' -o /dev/null --max-time 2 "http://127.0.0.1:$1/" 2>/dev/null
}

# Starts the fixture and the bridge in front of it. Leaves STUB_PID,
# BRIDGE_PID and WORK set for the caller to clean up.
start_fixture() {
    STUB_PORT=$(free_port)
    BRIDGE_PORT=$(free_port)
    WORK=$(mktemp -d)

    # The SearXNG record is the only thing that has to change: it must name
    # this run's bridge instead of 8902. Both occurrences — the Hashtable key
    # and the search template — carry the port, and both must move together
    # or the applet would fetch from the key it never searches.
    sed 's|127\.0\.0\.1:8902|127.0.0.1:'"$BRIDGE_PORT"'|' JSENGINES.TXT \
        > "$WORK/JSENGINES.TXT"
    if ! diff -q JSENGINES.TXT "$WORK/JSENGINES.TXT" >/dev/null; then
        echo "fixture: SearXNG record repointed at 127.0.0.1:$BRIDGE_PORT"
    else
        echo "FAIL  the SearXNG record was not found in JSENGINES.TXT"
        echo "      (expected a record on 127.0.0.1:8902 to repoint)"
        exit 1
    fi

    python3 StubSearxng.py "$STUB_PORT" > "$WORK/stub.log" 2>&1 &
    STUB_PID=$!
    JSEARCH_SEARXNG="http://127.0.0.1:$STUB_PORT/search?format=json" \
        python3 SearxngBridge.py "$BRIDGE_PORT" > "$WORK/bridge.log" 2>&1 &
    BRIDGE_PID=$!

    i=0
    while [ "$i" -lt 50 ]; do
        listening "$STUB_PORT" && listening "$BRIDGE_PORT" && return 0
        i=$((i + 1))
        sleep 0.1
    done
    echo "FAIL  the fixture did not come up. stub log:"
    sed 's/^/      /' "$WORK/stub.log"
    echo "      bridge log:"
    sed 's/^/      /' "$WORK/bridge.log"
    exit 1
}

cleanup() {
    [ -n "$STUB_PID" ] && kill "$STUB_PID" 2>/dev/null
    [ -n "$BRIDGE_PID" ] && kill "$BRIDGE_PID" 2>/dev/null
    [ -n "$WORK" ] && rm -rf "$WORK"
    return 0
}

# ------------------------------------------------------------------ the checks

# The assertions every mode shares: the applet booted, built its table, and
# searched. LOG must name the file to check.
check_trail() {
    echo "Checking the applet's evidence trail in ${LOG}:"
    echo

    # The applet booted and built its UI, rather than dying in init().
    expect "[runner] engines in table" "the applet started and built the engine table"

    # The claim docs/ANALYSIS.md makes about the URL-keyed Hashtable: all five
    # records load as five engines. Record 1's key carries a trailing space, so the
    # two Google keys stayed distinct and nothing was dropped. If someone trims
    # that space, this is the assertion that notices.
    expect "[runner] engines in table = 5" "all 5 engine records loaded (no URL-key collision)"

    # The engine file defines 5 records in 2 categories: GB_Chinese Google, Baidu,
    # LocalDemo and SearXNG are Chinese, plain Google is English. Selecting the
    # Chinese category therefore lists 4 of the 5 — and in particular NOT English
    # Google. This is the fact the prose kept getting wrong ("the Chinese category
    # lists both Google entries" — it never did; the two Googles sit in different
    # categories, which is a second, independent reason no collision was visible).
    count_is '^\[runner\] engine\[[0-9]\+\] = ' 4 "the Chinese category lists 4 of the 5 engines"
    refute "= Google / Google" "English Google is absent from the Chinese category"

    # The fifth record is the SearXNG one. In check mode the bridge behind it is
    # opt-in and CI does not run it, so this asserts the record parses and lands
    # in the Chinese category — not that the bridge answers. --live and
    # --combined assert the answering part.
    expect "= SearXNG / live web aggregation" "the SearXNG record loads as a Chinese engine"

    # The runner's deterministic setup found and selected the only engine that can
    # still answer out of the box: the local demo server.
    expect "= LocalDemo" "LocalDemo is in the Chinese list"
    expect "  [selected]" "an engine was selected for the search"

    expect "[runner] search started for 'JSearch'" "the applet started the search itself"

    # The timeout branch, which would otherwise pass as a quiet run.
    refute "[runner] no results after 30s" "the run did not time out waiting for results"

    # The preview pipeline (selecting result 0, its ResultsDetails, the edit
    # control). The Chinese text is not asserted on; the ASCII "2002" inside the
    # preview proves a real record was read back out of the original's own table.
    expect "[runner] preview of first result:" "the preview of the first result rendered"
    expect "2002" "the preview came from a real parsed record"
}

# ------------------------------------------------------------------ the modes

if [ "$MODE" = "check" ]; then
    LOG="${1:--}"
    [ "$LOG" = "--" ] && LOG=/dev/stdin
    check_trail

    echo
    # The 2002 scraper, unchanged. The page carries three result blocks; the
    # third repeats the first block's URL, so 2 is the correct count and is the
    # original dedup working rather than a result being lost.
    #
    # This is also the proof that the fetch really happened: two scraped
    # results cannot appear without a 200 whose body parsed. The demo server's
    # own access line is deliberately not asserted — run.sh reuses an
    # already-listening 8901, so that line only lands in the log of the run
    # that happened to start the server, which makes it environment-dependent
    # rather than meaningful.
    expect "[runner] results in list = 2" "the scraper found 3 blocks and dedup kept 2"
else
    # --live / --combined. Everything they need is built here, so the mode is
    # reproducible on a machine with no SearXNG and no network.

    # Keep a log the caller can look at, rather than only a temp one. Made
    # absolute before the cd below, because the run writes to it afterwards
    # and a relative path would otherwise land in runner/.
    LOG="${1:-$(mktemp)}"
    case "$LOG" in
        /*) ;;
        *) LOG="$PWD/$LOG" ;;
    esac
    cd "$(dirname "$0")" || exit 1

    trap cleanup EXIT INT TERM
    start_fixture

    if [ "$MODE" = "live" ]; then
        SPEC=SearXNG
        echo "mode:    the applet searches the fixture SearXNG through the bridge"
    else
        SPEC=LocalDemo,SearXNG
        echo "mode:    the applet searches the demo engine and the fixture SearXNG together"
    fi
    echo "log:     $LOG"

    # The applet's window never closes, so cap the run; 124 is the expected
    # exit. Without a DISPLAY, run under xvfb the way the CI job does.
    if [ -n "$DISPLAY" ]; then
        timeout 90 ./run.sh "file:$WORK/" --go --engine="$SPEC" --levels=2 \
            > "$LOG" 2>&1
    elif command -v xvfb-run >/dev/null 2>&1; then
        xvfb-run -a --server-args="-screen 0 1024x768x24" \
            timeout 90 ./run.sh "file:$WORK/" --go --engine="$SPEC" --levels=2 \
            > "$LOG" 2>&1
    else
        echo "FAIL  the applet needs a display, and there is neither a DISPLAY"
        echo "      nor an xvfb-run on this machine. Set DISPLAY, or install"
        echo "      xvfb (apt-get install xvfb)."
        exit 1
    fi

    check_trail

    # The level loop is the part only this mode can reach: the applet asks for
    # a second page per engine, the bridge translates it, and the 2002
    # scraper has to read it. Every URL below exists only in the fixture, so
    # each one present is that chain having worked end to end.
    echo
    echo "Checking the live path through the bridge:"

    expect "[runner] levels = 2" "the applet's own level loop was set to two pages"

    # The engine list prints one engine per line and never the spec itself, so
    # a comma-separated spec is checked name by name — and then the selection
    # markers are counted, because a name being listed is not the same as it
    # being searched.
    WANTED=0
    OLDIFS=$IFS
    IFS=,
    for name in $SPEC; do
        [ -n "$name" ] || continue
        WANTED=$((WANTED + 1))
        expect "= $name" "$name is in the Chinese list"
    done
    IFS=$OLDIFS
    count_fixed '  [selected]' "$WANTED" "exactly $WANTED engines were selected"

    # The bridge re-emits the fixture's page one. Its last block is the one a
    # page without a terminator loses, so stub-third present is the terminator
    # fix holding; stub-page-two-* present is level two having been fetched.
    expect "https://example.org/stub-third" "the last block of page one was not lost"
    expect "https://example.org/stub-page-two-first" "level two fetched the second page"
    expect "https://example.org/stub-page-two-second" "the last block of page two was not lost"

    # The bridge's own failure block. If the upstream is unreachable the
    # bridge still answers 200 with this in it, so its absence is the only
    # way to tell a translated page from an error page.
    refute "bridge-error" "the bridge never had to report an upstream failure"

    if [ "$MODE" = "live" ]; then
        count_is '^\[runner\] result\[[0-9]\+\] = ' 5 "all five fixture results reached the list"
        expect "[runner] status[0] = Results:5 From:SearXNG" \
            "the engine's own status line counted all five"
    else
        # Two engines, one URL in common (the fixture's page one reuses the
        # demo server's first URL on purpose). The demo engine contributes its
        # two; the bridge scrapes five and adds four, because the fifth is the
        # URL the demo engine already put in the table.
        count_is '^\[runner\] result\[[0-9]\+\] = ' 6 "six results: two demo + four new from the bridge"
        expect "Results:2 From:LocalDemo" "the demo engine delivered its two results"
        expect "Results:4 From:SearXNG" "the bridge scraped five and added four — the fifth was shared"
        count_fixed 'http://localhost:8901/jsearch-reborn' 1 \
            "the URL both engines returned is in the list exactly once"
        expect "| from: LocalDemo" "results are credited to the demo engine"
        expect "| from: SearXNG" "results are credited to the bridge engine"
    fi
fi

echo
if [ "$FAILURES" -eq 0 ]; then
    echo "PASS — all $CHECKS checks: the original 2002 applet runs and its scraper and dedup still work."
    exit 0
fi

echo "FAIL — $FAILURES of $CHECKS checks failed."
exit 1
