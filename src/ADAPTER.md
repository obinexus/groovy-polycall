# Groovy adapter

The public implementation lives under `main/groovy/org/obinexus/polycall`.
It crosses the JNI/FFI boundary only through:

    status = polycall_ffi_run_config("groovy-polycallrc", /*run=*/1)

`Polycall.runConfig` returns the core status unchanged; `runConfigOrThrow`
raises a `PolycallException` for non-zero statuses. No configuration parsing or
core runtime logic belongs in this binding.
