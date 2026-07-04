# TODO — groovy-polycall

Status: implemented thin Groovy/JNI adapter for libpolycall 1.5.

- [x] Publishable `@obinexusltd/groovy-polycall` npm source package
- [x] Groovy API with status-returning and exception-based calls
- [x] JNI string marshalling and native library loading
- [x] Exact `polycall_ffi_run_config(config_path, 1)` forwarding
- [x] Runnable example under `examples/`
- [x] Native forwarding test and Groovy/JNI smoke test
- [x] Thin-adapter source audit for Windows and POSIX shells
- [ ] Exercise the JNI smoke test in release CI across Windows, Linux, and macOS
- [ ] Publish signed platform-native artifacts alongside the source package

Do not add configuration parsing or runtime policy here; adapt the core only.
