#!/bin/sh
# Run the groovy-polycall test suite (Gradle + JUnit 5) against the REAL
# installed libpolycall.
#
#   POLYCALL_LIBRARY=/opt/polycall/lib/libpolycall.so.1 \
#   POLYCALL_CLI=/opt/polycall/bin/polycall sh scripts/test.sh
#
# Exit status: 0 = all tests ran and passed, 1 = failure,
# 77 = SKIP (a required toolchain is missing; nothing was tested).
set -u
cd "$(dirname "$0")/.." || exit 1

skip() { echo "SKIP: $*"; exit 77; }

command -v java >/dev/null 2>&1 || skip "java not found (JDK 22+ required)"
GRADLE=${GRADLE:-gradle}
command -v "$GRADLE" >/dev/null 2>&1 || skip "gradle not found (Gradle 8.10+ required; set GRADLE=...)"
JV=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')
case "$JV" in
  1.*|9|1[0-9]|2[01]) skip "java $JV found; the Foreign Function & Memory API needs JDK 22+" ;;
esac

if [ -z "${POLYCALL_CLI:-}" ] && command -v polycall >/dev/null 2>&1; then
  POLYCALL_CLI=$(command -v polycall); export POLYCALL_CLI
fi

# clearly-labelled fake ABI-2 library for the loader's mismatch test only
if [ -z "${POLYCALL_TEST_FAKE_ABI2:-}" ] && command -v cc >/dev/null 2>&1; then
  mkdir -p build/fake
  if cc -shared -fPIC -o build/fake/libfake_polycall_abi2.so src/test/c/fake_polycall_abi2.c 2>/dev/null; then
    POLYCALL_TEST_FAKE_ABI2=$(pwd)/build/fake/libfake_polycall_abi2.so; export POLYCALL_TEST_FAKE_ABI2
  fi
fi

echo "java: $(java -version 2>&1 | head -n 1)"
echo "POLYCALL_LIBRARY=${POLYCALL_LIBRARY:-<platform default>} POLYCALL_CLI=${POLYCALL_CLI:-<none>}"
exec "$GRADLE" --no-daemon --console=plain test "$@"
