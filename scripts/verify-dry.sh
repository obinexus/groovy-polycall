#!/usr/bin/env sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if grep -E -n 'fopen|open\(|CreateFile|sscanf|strtok|socket\(|connect\(' \
    "$root/c_src/groovy_polycall.c" "$root/c_src/groovy_polycall_jni.c"; then
    echo "groovy-polycall must not parse configuration or implement runtime logic" >&2
    exit 1
fi

grep -F -q 'polycall_ffi_run_config(config_path, 1)' \
    "$root/c_src/groovy_polycall.c"
grep -F -q 'GetStringUTFChars' \
    "$root/c_src/groovy_polycall_jni.c"
grep -F -q 'ReleaseStringUTFChars' \
    "$root/c_src/groovy_polycall_jni.c"

echo "groovy-polycall thin-adapter check: PASS"
