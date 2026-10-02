# @obinexusltd/groovy-polycall

Groovy binding for the [Polycall](https://github.com/obinexus/polycall) core
library, **binding ABI v1** (`polycall.h`, documented in the core's
`docs/BINDING_ABI.md`).

The binding calls `polycall.dll` (Windows, MSVC build), `libpolycall.dll`
(MinGW) or `libpolycall.so.1` (Linux) directly through the Java Foreign
Function & Memory API (`java.lang.foreign`, JDK 22+), from `@CompileStatic`
Groovy. There is no JNI shim and no C code to compile.

## Requirements

- JDK 22 or newer (tested with JDK 25 on Linux and Windows, JDK 27 on Windows)
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
TLS), call against `polycall start` and `polycall daemon start`, two-node
exchange both ways with empty/UTF-8/binary/1 MiB/1 MiB+1 payloads, registry
ownership, duplicates, auth, dead peer, timeouts, too-small buffers,
cancel/close, invalid handles, concurrent senders, and interop with
`polycall peer serve/send/recv/health/register`. With `POLYCALL_INTEROP_ECHO`
naming another binding's echo agent it also exchanges payloads with that
binding. Checks that cannot run are reported as skipped, never passed;
`scripts/test.sh` exits 77 when JDK 22+ or Gradle is missing.
`src/test/c/fake_polycall_abi2.c` is a clearly-labelled fake library used only
for the loader's ABI-mismatch test.

The npm package is a source distribution: `require('@obinexusltd/groovy-polycall')`
returns the paths of the packaged sources and manifests.

## Author and license

Copyright © 2026 Nnamdi Michael Okpala <okpalan@protonmail.com>.
Released under the [MIT License](LICENSE).
