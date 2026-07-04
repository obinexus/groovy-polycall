# @obinexusltd/groovy-polycall

Groovy/JNI binding for [libpolycall](https://github.com/obinexus/libpolycall)
1.5. The adapter maps Groovy calls to the single core entry point:

```c
polycall_ffi_run_config(config_path, 1)
```

Configuration parsing, validation, networking, and runtime policy remain in
libpolycall. This package only marshals the configuration path across JNI and
returns the core status unchanged.

## Install the source package

```shell
npm install @obinexusltd/groovy-polycall
```

The npm package publishes the complete Groovy, JNI, and C source tree. It is a
native source distribution rather than a JavaScript implementation. In Node.js,
`require('@obinexusltd/groovy-polycall')` returns absolute paths to the packaged
sources, headers, configuration, manifest, and build files.

## Requirements

- libpolycall 1.5 development library and headers
- JDK 17 or newer
- Groovy 4 or a Gradle installation with its bundled Groovy runtime
- a C11 compiler and GNU Make

## Build

Build the standalone adapter archive without linking the core:

```shell
make
```

Build the JNI shared library by supplying the JDK location and the linker flags
for libpolycall:

```shell
export JAVA_HOME=/path/to/jdk
export POLYCALL_LDFLAGS='-L/path/to/lib -lpolycall'
make jni
```

PowerShell uses the same variables:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:POLYCALL_LDFLAGS = '-LC:\path\to\lib -lpolycall'
make jni
```

Compile the Groovy classes with either Gradle or `groovyc`:

```shell
gradle classes
# or
groovyc -d build/classes src/main/groovy/org/obinexus/polycall/*.groovy
```

Place the JNI library on `java.library.path`, or pass its absolute path with
`-Dgroovy.polycall.library=/absolute/path/to/the/library`.

## API

```groovy
import org.obinexus.polycall.Polycall

int status = Polycall.runConfig('groovy-polycallrc')
Polycall.runConfigOrThrow('groovy-polycallrc')
```

- `runConfig` returns the exact libpolycall status.
- `runConfigOrThrow` raises `PolycallException` for a non-zero status.
- Omitting the path uses `groovy-polycallrc`.
- `groovy.polycall.library` selects an explicit JNI library file.

See [`examples/basic.groovy`](examples/basic.groovy) for a runnable example.

## Verification

The default suite needs only a C compiler, Make, Node.js, and PowerShell on
Windows:

```shell
npm test
```

It verifies exact path forwarding, the required validation flag, status
propagation, thin-adapter constraints, and npm package completeness.

When Groovy and a JDK matching the native compiler architecture are installed,
run the end-to-end JNI smoke test:

```shell
npm run test:groovy
```

## Package layout

- `src/main/groovy/` — public Groovy API and exception type
- `c_src/` — C adapter and JNI bridge
- `include/` — Groovy adapter C header
- `generated/polycall/` — minimal generated core FFI declaration
- `examples/` — Groovy usage example and sample configuration
- `tests/` — native mock, JNI smoke test, and npm package test

## Author and license

Copyright © 2026 Nnamdi Michael Okpala
<okpalan@protonmail.com>.

Released under the [MIT License](LICENSE).
