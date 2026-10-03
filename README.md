# groovy-polycall

Groovy binding for the [Polycall](https://github.com/obinexus/polycall) core
library, **binding ABI v1** (`polycall.h`, documented in the core's
`docs/BINDING_ABI.md`).

The binding calls `polycall.dll` (Windows, MSVC build), `libpolycall.dll`
(MinGW) or `libpolycall.so.1` (Linux) directly through the Java Foreign
Function & Memory API (`java.lang.foreign`, JDK 22+), from `@CompileStatic`
Groovy. There is no JNI shim and no C code to compile.

## Requirements

- JDK 22 or newer (tested with JDK 25 on Linux and Windows, JDK 27 on Windows;
  see the JDK 27 note under Tests)
- Groovy 4 (`org.apache.groovy:groovy:4.0.33`), Gradle 8.10+ to build
- libpolycall >= 1.1.0 (binding ABI 1), 64-bit

## Loading the library

`POLYCALL_LIBRARY` (full path) first, then the `polycall.library` system
property, then `polycall.dll` / `libpolycall.dll` (Windows), `libpolycall.so.1`
(Linux), `libpolycall.1.dylib` (macOS) through the OS search. Every symbol is
resolved up front and `polycall_ffi_abi_version()` must be 1; otherwise a
`PolycallLoadException` names the library and the problem. Run the JVM with
`--enable-native-access=ALL-UNNAMED`.

## API

```groovy
import org.obinexus.polycall.Peer
import org.obinexus.polycall.Polycall

int status = Polycall.runConfig('groovy-polycallrc')   // polycall_ffi_run_config(path, 1), unchanged status
Polycall.runConfigOrThrow('groovy-polycallrc')        // PolycallException(status, statusName, detail)
Polycall.runConfigOrThrow(path, false)                // validate only
Polycall.describe(path)
Polycall.call('127.0.0.1:7000', 'inventory', 'get', '{"item_id":"widget-a"}', 2000)

Peer.open('alpha', '127.0.0.1:0', token).withCloseable { Peer alpha ->
    Peer.open('beta', '127.0.0.1:0', token).withCloseable { Peer beta ->
        alpha.register('beta', beta.endpoint)
        alpha.send('beta', bytes, 'msg-1', 5000)      // exactly one delivery attempt
        def m = beta.recv(5000)                       // m.sender, m.messageId, m.payload
    }
}
```

`Peer` covers open / close / endpoint / nodeId / register / unregister / list /
ping / send / recv / tryRecv / cancel / health; it is thread-safe, `close()` is
idempotent and wakes blocked receivers. See [`examples/basic.groovy`](examples/basic.groovy).

## Tests

```sh
POLYCALL_LIBRARY=/opt/polycall/lib/libpolycall.so.1 \
POLYCALL_CLI=/opt/polycall/bin/polycall sh scripts/test.sh     # or: gradle test
```

The JUnit 5 suite (Groovy test classes) runs against the real library and the
real `polycall` CLI: version/ABI, run_config (valid, missing, invalid, strict,
TLS, a non-ASCII directory and file name), call against `polycall start` and
`polycall daemon start`, two-node exchange both ways with
empty/UTF-8/binary/1 MiB/1 MiB+1 payloads, registry ownership, duplicates,
auth, dead peer, timeouts, too-small buffers, cancel/close, invalid handles, a
dropped peer closed by its Cleaner, out-of-range `uint32_t` timeouts,
concurrent senders and calls, and interop with
`polycall peer serve/send/recv/health/register`. With `POLYCALL_INTEROP_ECHO`
naming another binding's echo agent it also exchanges payloads with that
binding. Checks that cannot run are reported as skipped, never passed;
`scripts/test.sh` exits 77 when JDK 22+ or Gradle is missing.

Loader errors are checked in-process and in a fresh JVM (`LoaderProcessTest`:
`PolycallLoadException` naming the library, no crash): a missing file, a
library without the binding ABI, a real 1.0 core when
`POLYCALL_TEST_V1_0_LIBRARY` names one (libpolycall built from polycall
v1.0.0), and an ABI-2 library when `POLYCALL_TEST_FAKE_ABI2` names one.
`src/test/c/fake_polycall_abi2.c` is that clearly-labelled fake library
(`scripts/test.sh` builds it when a C compiler is present); it is used only
for the loader's ABI-mismatch test.

On Windows, run `gradle test` with `POLYCALL_LIBRARY` set to `polycall.dll`
(MSVC build) or `libpolycall.dll` (MSYS2 UCRT64 build) and `POLYCALL_CLI` to
the matching `polycall.exe`. A JVM runs in the ANSI code page, so non-ASCII
configuration paths need a core that opens files by UTF-8 path (polycall
commit 58bae1b or later); older DLLs report `POLYCALL_E_NOT_FOUND`.

**JDK 27:** Gradle 9.7 cannot compile this Groovy DSL build script while it
runs on JDK 27 itself (its embedded Groovy rejects class file version 71).
Run Gradle on an earlier JDK (we use 25) and the tests on JDK 27 through a
toolchain: `gradle test -PtestJavaVersion=27`. The binding and Groovy 4.0.33
themselves run on JDK 27.

## Packaging

The Maven publication `org.obinexus:groovy-polycall` (jar, sources jar, POM
with Groovy 4.0.33 as its only runtime dependency) can be built into a
directory inside `build/` -- nothing is uploaded:

```sh
gradle publishMavenPublicationToBuildRepoRepository    # -> build/repo
```

The npm package is a source distribution: `require('groovy-polycall')`
returns the paths of the packaged sources and manifests.

## Author and license

Copyright © 2026 Nnamdi Michael Okpala <okpalan@protonmail.com>.
Released under the [MIT License](LICENSE).
