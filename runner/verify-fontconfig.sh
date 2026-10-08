#!/bin/sh
# Exercise the real fontconfig branch in run.sh under a faked `uname`, so both
# platform decisions are checked without needing a Windows JDK or a Linux JDK.
#
# The block is extracted from run.sh itself rather than copied, so this cannot
# drift from the code it is testing.
#
# Usage: ./verify-fontconfig.sh

cd "$(dirname "$0")"

# Pull the FONTCONFIG_FLAG block out of run.sh, stopping at the exec line.
BLOCK=$(sed -n '/^FONTCONFIG_FLAG=""/,/^esac$/p' run.sh)
if [ -z "$BLOCK" ]; then
    echo "FAIL  could not find the FONTCONFIG_FLAG block in run.sh"
    exit 1
fi

# Stand in for the tools run.sh calls, so the test needs neither a Windows
# JDK path nor a Cygwin installation. These are PATH shims rather than shell
# functions: the block is evaluated in a child `sh`, which would not inherit a
# function and would silently fall back to the real uname — making every
# case look like Linux and passing for the wrong reason.
SHIM=$(mktemp -d)
trap 'rm -rf "$SHIM"' EXIT

cat > "$SHIM/uname" <<'EOF'
#!/bin/sh
echo "$FAKE_UNAME"
EOF
cat > "$SHIM/cygpath" <<'EOF'
#!/bin/sh
echo "C:/fake/path"
EOF
chmod +x "$SHIM/uname" "$SHIM/cygpath"
PATH="$SHIM:$PATH"
export PATH

FAILURES=0

# $1 = what uname -s reports, $2 = whether the flag should be set
case_is() {
    WANTED="$2"
    # Confirm the shim is actually in force before trusting the result.
    SEEN=$(FAKE_UNAME="$1" uname -s)
    if [ "$SEEN" != "$1" ]; then
        echo "  FAIL  could not fake uname (asked for '$1', got '$SEEN')"
        FAILURES=$((FAILURES + 1))
        return
    fi
    GOT=$(FAKE_UNAME="$1" sh -c "$BLOCK
        printf '%s' \"\$FONTCONFIG_FLAG\"")
    if [ -n "$GOT" ] && [ "$WANTED" = "yes" ]; then
        echo "  ok    $1 -> fontconfig applied ($GOT)"
    elif [ -z "$GOT" ] && [ "$WANTED" = "no" ]; then
        echo "  ok    $1 -> fontconfig not applied, AWT keeps platform config"
    else
        echo "  FAIL  $1 -> expected applied=$WANTED, got '$GOT'"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "Checking run.sh's platform decision:"
echo
# Git Bash / MSYS on Windows: the bundled file is wanted.
case_is "MINGW64_NT-10.0-19045" yes
case_is "MSYS_NT-10.0" yes
case_is "CYGWIN_NT-10.0" yes
# Everywhere else the bundled file names Windows faces, so it must not be used.
case_is "Linux" no
case_is "Darwin" no

echo
if [ "$FAILURES" -eq 0 ]; then
    echo "PASS  the Windows CJK font shim is applied on Windows only."
    exit 0
fi
echo "FAIL  $FAILURES platform(s) took the wrong branch."
exit 1
