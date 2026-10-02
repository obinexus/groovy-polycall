package org.obinexus.polycall

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

import static java.lang.foreign.ValueLayout.JAVA_LONG

/**
 * Groovy binding for the Polycall binding ABI v1 (polycall.h). Calls
 * polycall.dll / libpolycall.so.1 directly through the Java Foreign Function
 * &amp; Memory API (JDK 22+); there is no JNI shim. Thread-safe. Run the JVM
 * with --enable-native-access=ALL-UNNAMED.
 */
@CompileStatic
final class Polycall {
    /** The binding ABI this binding implements; the library must report the same. */
    static final int ABI_VERSION = 1
    static final String DEFAULT_CONFIG = 'groovy-polycallrc'
    static final int PEER_MAX_PAYLOAD = 1 << 20
    static final int CALL_MAX_OUTPUT = 1 << 20
    @PackageScope static final long ID_MAX = 64L

    private Polycall() {}

    @PackageScope
    static NativeApi api() {
        NativeApi.get()
    }

    /** The library name or path that was loaded. */
    static String libraryName() {
        api().library
    }

    static int abiVersion() {
        (int) api().abiVersion.invokeWithArguments()
    }

    /** polycall_ffi_version(), e.g. "1.1.0". */
    static String version() {
        Arena arena = Arena.ofConfined()
        try {
            int cap = 32
            while (true) {
                MemorySegment buf = arena.allocate((long) cap)
                int n = (int) api().ffiVersion.invokeWithArguments(buf, cap)
                if (n < 0) throw error(n, 'polycall_ffi_version')
                if (n < cap) return buf.getString(0L)
                cap = n + 1
            }
        } finally {
            arena.close()
        }
    }

    /** polycall_get_version() (1.0 API, kept by the library). */
    static String coreVersion() {
        cString((MemorySegment) api().getVersion.invokeWithArguments())
    }

    /** polycall_strerror(status). */
    static String strerror(int status) {
        cString((MemorySegment) api().strerror.invokeWithArguments(status))
    }

    /** polycall_last_error(): this thread's detail for its most recent failed call. */
    static String lastError() {
        Arena arena = Arena.ofConfined()
        try {
            long cap = 1024L
            while (true) {
                MemorySegment buf = arena.allocate(cap)
                int n = (int) api().lastError.invokeWithArguments(buf, cap)
                if (n < cap) return buf.getString(0L)
                cap = n + 1L
            }
        } finally {
            arena.close()
        }
    }

    /**
     * The binding's documented entry point: polycall_ffi_run_config(path, 1)
     * (0 with strict = false), returning the unchanged core status.
     */
    static int runConfig(String configPath = DEFAULT_CONFIG, boolean strict = true) {
        Arena arena = Arena.ofConfined()
        try {
            return (int) api().runConfig.invokeWithArguments(cstr(arena, configPath, 'configPath'), strict ? 1 : 0)
        } finally {
            arena.close()
        }
    }

    /** Like runConfig but throws PolycallException (status, name, detail) on failure. */
    static void runConfigOrThrow(String configPath = DEFAULT_CONFIG, boolean strict = true) {
        int st = runConfig(configPath, strict)
        if (st != Status.OK) throw error(st, "run_config(${configPath})".toString())
    }

    /** polycall_ffi_describe(): JSON description of a configuration file. */
    static String describe(String configPath) {
        Arena arena = Arena.ofConfined()
        try {
            MemorySegment path = cstr(arena, configPath, 'configPath')
            int cap = 64 * 1024
            while (true) {
                MemorySegment buf = arena.allocate((long) cap)
                int n = (int) api().describe.invokeWithArguments(path, buf, cap)
                if (n < 0) throw error(n, "describe(${configPath})".toString())
                if (n < cap) return buf.getString(0L)
                cap = n + 1
            }
        } finally {
            arena.close()
        }
    }

    /**
     * One polycall_rpc v1 round trip (polycall_call) to a running runtime.
     * inputJson null is sent as null; timeoutMs must be 1..600000.
     */
    static String call(String endpoint, String service, String operation, String inputJson, long timeoutMs) {
        Arena arena = Arena.ofConfined()
        try {
            MemorySegment ep = cstr(arena, endpoint, 'endpoint')
            MemorySegment svc = cstr(arena, service, 'service')
            MemorySegment op = cstr(arena, operation, 'operation')
            MemorySegment input = cstrOrNull(arena, inputJson, 'inputJson')
            int timeout = uint32(timeoutMs, 'timeoutMs')
            long cap = CALL_MAX_OUTPUT + 1L
            MemorySegment out = arena.allocate(cap)
            MemorySegment outLen = arena.allocate(JAVA_LONG)
            int st = (int) api().call.invokeWithArguments(ep, svc, op, input, timeout, out, cap, outLen)
            if (st == Status.OK) return out.getString(0L)
            long len = outLen.get(JAVA_LONG, 0L)
            String ctx = "${service}.${operation} at ${endpoint}".toString()
            if (st == Status.E_TOO_LARGE) throw error(st, ctx, null, len)
            if (len > 0) throw error(st, ctx, out.getString(0L), -1L)
            throw error(st, ctx)
        } finally {
            arena.close()
        }
    }

    // ---- internals shared with Peer ----------------------------------------------

    @PackageScope
    static String cString(MemorySegment p) {
        p == MemorySegment.NULL ? '' : p.reinterpret((long) Integer.MAX_VALUE).getString(0L)
    }

    @PackageScope
    static PolycallException error(int status, String context, String remote = null, long required = -1L) {
        String detail = lastError()
        new PolycallException(status, strerror(status), detail, context, remote, required)
    }

    @PackageScope
    static PolycallException argumentError(String detail) {
        new PolycallException(Status.E_INVALID_ARGUMENT, strerror(Status.E_INVALID_ARGUMENT), detail, 'binding')
    }

    @PackageScope
    static void check(int status, String context) {
        if (status != Status.OK) throw error(status, context)
    }

    @PackageScope
    static MemorySegment cstr(Arena arena, String s, String name) {
        if (s == null) throw argumentError("${name} is null".toString())
        if (s.indexOf('\u0000') >= 0) throw argumentError("${name} contains a NUL character".toString())
        arena.allocateFrom(s)
    }

    @PackageScope
    static MemorySegment cstrOrNull(Arena arena, String s, String name) {
        s == null ? MemorySegment.NULL : cstr(arena, s, name)
    }

    @PackageScope
    static int uint32(long value, String name) {
        if (value < 0L || value > 0xFFFF_FFFFL) throw argumentError("${name} must be 0..${0xFFFF_FFFFL}, got ${value}".toString())
        (int) value
    }

    @PackageScope
    static int invokeInt(MethodHandle h, Object... args) {
        (int) h.invokeWithArguments(args)
    }

    /** A text-returning call with out_len (snprintf rules): grow the buffer and retry. */
    @PackageScope
    static String text(String context, Closure<Integer> call) {
        Arena arena = Arena.ofConfined()
        try {
            long cap = 4096L
            MemorySegment outLen = arena.allocate(JAVA_LONG)
            while (true) {
                MemorySegment buf = arena.allocate(cap)
                int st = call.call(buf, cap, outLen)
                if (st == Status.OK) return buf.getString(0L)
                long need = outLen.get(JAVA_LONG, 0L)
                if (st == Status.E_TOO_LARGE && need >= cap) {
                    cap = need + 1L
                    continue
                }
                throw error(st, context)
            }
        } finally {
            arena.close()
        }
    }
}
