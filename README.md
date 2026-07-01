# groovy-polycall

**Groovy** binding for [libpolycall](https://github.com/obinexus/libpolycall) — a
scaffolded adapter (TODO tracked).

This binding is a *thin adapter*: it contains no runtime or config logic. It
maps Groovy idioms onto the shared C core through the FFI boundary
(`include/polycall/polycall.h`, `include/polycall/polycall_ffi.h`). See
[../../docs/adapter-pattern.md](../../docs/adapter-pattern.md).

## Config

Read-only config: [`groovy-polycallrc`](groovy-polycallrc) — the standard `*polycallrc` convention,
mapping onto the single shared schema. No per-language parser exists.

## Manifest

See [`polycall-binding.json`](polycall-binding.json).

## Build the core first

```bash
cd ../.. && ./setup.sh      # produces build/libpolycall.a + the shared library
```

## Status

🧩 Scaffolded. Wiring tracked in [TODO.md](TODO.md) and [../../docs/release/TODO-1.5.md](../../docs/release/TODO-1.5.md).
