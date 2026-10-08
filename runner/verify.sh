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
# Usage: ./verify.sh [LOGFILE]        (default: stdin)
#
# Exits 0 if the trail is complete, 1 otherwise. All checks run, so one run
# reports every failure rather than only the first.

LOG="${1:--}"
[ "$LOG" = "--" ] && LOG=/dev/stdin

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

echo "Checking the applet's evidence trail in ${LOG}:"
echo

# The applet booted and built its UI, rather than dying in init().
expect "[runner] engines in table" "the applet started and built the engine table"

# The claim docs/ANALYSIS.md makes about the URL-keyed Hashtable: all four
# records load as four engines. Record 1's key carries a trailing space, so the
# two Google keys stayed distinct and nothing was dropped. If someone trims
# that space, this is the assertion that notices.
expect "[runner] engines in table = 4" "all 4 engine records loaded (no URL-key collision)"

# The engine file defines 4 records in 2 categories: GB_Chinese Google and
# Baidu and LocalDemo are Chinese, plain Google is English. Selecting the
# Chinese category therefore lists 3 of the 4 — and in particular NOT English
# Google. This is the fact the prose kept getting wrong ("the Chinese category
# lists both Google entries" — it never did; the two Googles sit in different
# categories, which is a second, independent reason no collision was visible).
count_is '^\[runner\] engine\[[0-9]\+\] = ' 3 "the Chinese category lists 3 of the 4 engines"
refute "[runner] engine[3] = " "English Google is absent from the Chinese category"

# The runner's deterministic setup found and selected the only engine that can
# still answer: the local demo server.
expect "[runner] engine[0] = LocalDemo" "LocalDemo is in the Chinese list"
expect "  [selected]" "exactly the demo engine was selected for the search"

expect "[runner] search started for 'JSearch'" "the applet started the search itself"

# The 2002 scraper, unchanged. The page carries three result blocks; the third
# repeats the first block's URL, so 2 is the correct count and is the original
# dedup working rather than a result being lost.
#
# This is also the proof that the fetch really happened: two scraped results
# cannot appear without a 200 whose body parsed. The demo server's own access
# line is deliberately not asserted — run.sh reuses an already-listening
# 8901, so that line only lands in the log of the run that happened to start
# the server, which makes it environment-dependent rather than meaningful.
expect "[runner] results in list = 2" "the scraper found 3 blocks and dedup kept 2"

# The timeout branch, which would otherwise pass as a quiet run.
refute "[runner] no results after 30s" "the run did not time out waiting for results"

# The preview pipeline (selecting result 0, its ResultsDetails, the edit
# control). The Chinese text is not asserted on; the ASCII "2002" inside the
# preview proves a real record was read back out of the original's own table.
expect "[runner] preview of first result:" "the preview of the first result rendered"
expect "2002" "the preview came from a real parsed record"

echo
if [ "$FAILURES" -eq 0 ]; then
    echo "PASS — all $CHECKS checks: the original 2002 applet runs and its scraper and dedup still work."
    exit 0
fi

echo "FAIL — $FAILURES of $CHECKS checks failed."
exit 1
