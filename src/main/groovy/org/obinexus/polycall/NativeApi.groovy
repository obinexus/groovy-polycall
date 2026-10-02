package org.obinexus.polycall

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.invoke.MethodHandle

import static java.lang.foreign.ValueLayout.ADDRESS
import static java.lang.foreign.ValueLayout.JAVA_INT
import static java.lang.foreign.ValueLayout.JAVA_LONG

/**
 * The Polycall library could not be used: not found, a Binding ABI v1 symbol
 * is missing (an older 1.0 core) or polycall_ffi_abi_version() is not 1.
 * Raised instead of crashing on first use.
 */
@CompileStatic
class PolycallLoadException extends RuntimeException {
    final String library
    final String reason

    PolycallLoadException(String library, String reason, Throwable cause = null) {
        super("polycall: cannot use library '${library}': ${reason}".toString(), cause)
        this.library = library
        this.reason = reason
    }
}

/**
 * Method handles for every Binding ABI v1 function of polycall.h, resolved up
 * front through the Java Foreign Function & Memory API (JDK 22+, no C glue).
 * Loading order: POLYCALL_LIBRARY, the polycall.library system property, then
 * polycall.dll / libpolycall.dll (Windows), libpolycall.so.1 (Linux),
 * libpolycall.1.dylib (macOS).
 */
@CompileStatic
@PackageScope
final class NativeApi {
    static final String LIBRARY_ENV = 'POLYCALL_LIBRARY'
    static final String LIBRARY_PROPERTY = 'polycall.library'

    final String library
    final MethodHandle abiVersion, ffiVersion, strerror, lastError, getVersion, runConfig, describe, call
    final MethodHandle peerOpen, peerClose, peerEndpoint, peerNodeId, peerRegister, peerUnregister
    final MethodHandle peerList, peerPing, peerSend, peerRecv, peerCancel, peerHealth

    private NativeApi(String library, SymbolLookup lookup) {
        this.library = library
        Linker linker = Linker.nativeLinker()
        MemoryLayout sizeT = linker.canonicalLayouts().get('size_t')
        if (sizeT == null || sizeT.byteSize() != 8L) {
            throw new PolycallLoadException(library, "only 64-bit platforms are supported (size_t is ${sizeT?.byteSize()} bytes)".toString())
        }
        List<String> missing = []
        Closure<MethodHandle> h = { String name, FunctionDescriptor fd ->
            MemorySegment sym = lookup.find(name).orElse(null)
            if (sym == null) {
                missing << name
                return (MethodHandle) null
            }
            return linker.downcallHandle(sym, fd)
        }
        abiVersion = h('polycall_ffi_abi_version', FunctionDescriptor.of(JAVA_INT))
        ffiVersion = h('polycall_ffi_version', FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
        strerror = h('polycall_strerror', FunctionDescriptor.of(ADDRESS, JAVA_INT))
        lastError = h('polycall_last_error', FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG))
        getVersion = h('polycall_get_version', FunctionDescriptor.of(ADDRESS))
        runConfig = h('polycall_ffi_run_config', FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
        describe = h('polycall_ffi_describe', FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT))
        call = h('polycall_call', FunctionDescriptor.of(JAVA_INT,
                ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS))
        peerOpen = h('polycall_peer_open', FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
        peerClose = h('polycall_peer_close', FunctionDescriptor.of(JAVA_INT, JAVA_INT))
        peerEndpoint = h('polycall_peer_endpoint', FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG))
        peerNodeId = h('polycall_peer_node_id', FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG))
        peerRegister = h('polycall_peer_register', FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS))
        peerUnregister = h('polycall_peer_unregister', FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS))
        peerList = h('polycall_peer_list', FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS))
        peerPing = h('polycall_peer_ping', FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT))
        peerSend = h('polycall_peer_send', FunctionDescriptor.of(JAVA_INT,
                JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT))
        peerRecv = h('polycall_peer_recv', FunctionDescriptor.of(JAVA_INT,
                JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS))
        peerCancel = h('polycall_peer_cancel', FunctionDescriptor.of(JAVA_INT, JAVA_INT))
        peerHealth = h('polycall_peer_health', FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS))

        if (missing) {
            throw new PolycallLoadException(library, ("missing symbol(s) ${missing}: this is not a libpolycall " +
                    ">= 1.1.0 with binding ABI ${Polycall.ABI_VERSION} (an older 1.0 core?)").toString())
        }
        int abi = (int) abiVersion.invokeWithArguments()
        if (abi != Polycall.ABI_VERSION) {
            throw new PolycallLoadException(library,
                    "polycall_ffi_abi_version() returned ${abi}, this binding implements binding ABI ${Polycall.ABI_VERSION}".toString())
        }
    }

    static List<String> defaultNames() {
        String os = System.getProperty('os.name', '').toLowerCase(Locale.ROOT)
        if (os.contains('win')) return ['polycall.dll', 'libpolycall.dll']
        if (os.contains('mac') || os.contains('darwin')) return ['libpolycall.1.dylib']
        return ['libpolycall.so.1']
    }

    /** Load and verify one library (path or bare name for the OS loader). */
    static NativeApi load(String library) {
        SymbolLookup lookup
        try {
            lookup = SymbolLookup.libraryLookup(library, Arena.global())
        } catch (IllegalArgumentException | IllegalCallerException e) {
            throw new PolycallLoadException(library, "cannot load the library: ${e.message}".toString(), e)
        }
        return new NativeApi(library, lookup)
    }

    private static NativeApi loadDefault() {
        String explicit = System.getenv(LIBRARY_ENV)
        String source = LIBRARY_ENV
        if (!explicit?.trim()) {
            explicit = System.getProperty(LIBRARY_PROPERTY)
            source = "-D${LIBRARY_PROPERTY}".toString()
        }
        if (explicit?.trim()) {
            try {
                return load(explicit)
            } catch (PolycallLoadException e) {
                throw new PolycallLoadException(explicit, "${e.reason} (selected by ${source})".toString(), e)
            }
        }
        List<String> tried = []
        PolycallLoadException last = null
        for (String name : defaultNames()) {
            try {
                return load(name)
            } catch (PolycallLoadException e) {
                if (!e.reason.startsWith('cannot load the library')) throw e
                tried << name
                last = e
            }
        }
        throw new PolycallLoadException(tried.join(', '),
                "libpolycall was not found; set ${LIBRARY_ENV} to the full path of the library or put it on the system library path".toString(),
                last)
    }

    private static volatile Object state

    /** The process-wide library; a load failure is reported again on every call. */
    static NativeApi get() {
        Object s = state
        if (s == null) {
            synchronized (NativeApi) {
                s = state
                if (s == null) {
                    try {
                        s = loadDefault()
                    } catch (PolycallLoadException e) {
                        s = e
                    }
                    state = s
                }
            }
        }
        if (s instanceof NativeApi) return (NativeApi) s
        PolycallLoadException e = (PolycallLoadException) s
        throw new PolycallLoadException(e.library, e.reason, e)
    }
}
