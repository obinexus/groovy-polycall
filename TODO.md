# TODO — groovy-polycall

Status: Groovy 4 binding for the Polycall binding ABI v1 through the Java
Foreign Function & Memory API (JDK 22+), no JNI shim.

- [x] Every Binding ABI v1 function resolved up front (PolycallLoadException on
      missing library / missing symbol / ABI mismatch)
- [x] `Polycall.runConfig(path)` -> polycall_ffi_run_config(path, 1), status unchanged
- [x] describe / call / Peer (open, close, register, list, ping, send, recv, cancel, health)
- [x] PolycallException with status, name and detail
- [x] JUnit 5 suite against the real library and `polycall` CLI (Linux + Windows)
- [ ] Publish to Maven Central / npm (not done by QA)
- [ ] macOS run (libpolycall.1.dylib) — not tested
