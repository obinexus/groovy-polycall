# TODO — groovy-polycall (Groovy)

Status: 🧩 scaffolded adapter for libpolycall 1.5.0. Honestly not yet wired to
the core — this file tracks the remaining work. Rolled up in
[../../docs/release/TODO-1.5.md](../../docs/release/TODO-1.5.md).

- [x] Folder structure, manifest, and `groovy-polycallrc` (shared schema)
- [ ] Locate/generate Groovy FFI bindings for `polycall_ffi.h`
- [ ] Implement the thin adapter in `src/` (idioms -> `polycall_ffi_run_config`)
- [ ] Add a runnable example under `examples/`
- [ ] Add a smoke test under `tests/`
- [ ] Confirm `scripts/verify-dry.sh` passes (no core duplication)

Do not add config parsing or runtime logic here — adapt the core only.
