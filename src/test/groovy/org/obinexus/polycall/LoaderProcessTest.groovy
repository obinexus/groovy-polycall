package org.obinexus.polycall

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Test helper run in a fresh JVM by LoaderProcessTest: load the library
 * through the public API (POLYCALL_LIBRARY first) and report the result.
 * Exit 0 = loaded, 8 = PolycallLoadException.
 */
class LoaderProbe {
    static void main(String[] args) {
        try {
            println "loaded ${Polycall.libraryName()}: libpolycall ${Polycall.version()} (binding ABI ${Polycall.abiVersion()})"
        } catch (PolycallLoadException e) {
            System.err.println("${e.class.name}: ${e.message}")
            System.exit(8)
        }
    }
}

/**
 * The documented loading path end to end, in a fresh JVM per case:
 * POLYCALL_LIBRARY naming a missing file, a library without the binding ABI,
 * a real 1.0 core or an ABI-2 fake must raise PolycallLoadException with a
 * clear message naming the library -- never crash the JVM (no hs_err file,
 * no fatal-error banner).
 */
class LoaderProcessTest {
    @TempDir
    Path dir

    private Fixtures.Result probe(String library) {
        String java = Path.of(System.getProperty('java.home'), 'bin', Fixtures.WINDOWS ? 'java.exe' : 'java').toString()
        List<String> cmd = [java, '--enable-native-access=ALL-UNNAMED', '-cp', System.getProperty('java.class.path'),
                            LoaderProbe.name]
        def r = Fixtures.run(cmd, dir, ['POLYCALL_LIBRARY': library], 60_000L)
        assertFalse((r.out + r.stderr).contains('A fatal error has been detected'), r.out + r.stderr)
        Files.list(dir).withCloseable { files ->
            assertTrue(files.noneMatch { Path p -> p.fileName.toString().startsWith('hs_err') }, 'JVM crash log written')
        }
        r
    }

    private static void assertLoadError(Fixtures.Result r, String library, String reason) {
        assertEquals(8, r.exit, r.out + r.stderr)
        assertTrue(r.stderr.contains("PolycallLoadException: polycall: cannot use library '${library}'".toString()), r.stderr)
        assertTrue(r.stderr.contains(reason), r.stderr)
        assertTrue(r.stderr.contains('(selected by POLYCALL_LIBRARY)'), r.stderr)
    }

    @Test
    void 'the real library loads'() {
        String lib = System.getenv('POLYCALL_LIBRARY')
        Assumptions.assumeTrue(lib?.trim() as boolean, 'POLYCALL_LIBRARY is not set for this run')
        def r = probe(lib)
        assertEquals(0, r.exit, r.stderr)
        assertTrue(r.out.contains("loaded ${lib}: libpolycall ".toString()) && r.out.contains('(binding ABI 1)'), r.out)
    }

    @Test
    void 'missing library'() {
        String bogus = dir.resolve(Fixtures.WINDOWS ? 'no-such-polycall.dll' : 'libno-such-polycall.so.1').toString()
        assertLoadError(probe(bogus), bogus, 'cannot load the library')
    }

    @Test
    void 'library without the binding ABI'() {
        String other = Fixtures.WINDOWS ? 'kernel32.dll' : 'libc.so.6'
        assertLoadError(probe(other), other, 'missing symbol(s)')
    }

    @Test
    void 'real 1_0 core'() {
        String old = System.getenv('POLYCALL_TEST_V1_0_LIBRARY')
        Assumptions.assumeTrue(old?.trim() as boolean, 'POLYCALL_TEST_V1_0_LIBRARY not set (no libpolycall 1.0.x build provided)')
        def r = probe(old)
        assertLoadError(r, old, 'missing symbol(s)')
        assertTrue(r.stderr.contains('older 1.0 core'), r.stderr)
    }

    @Test
    void 'abi mismatch'() {
        String fake = System.getenv('POLYCALL_TEST_FAKE_ABI2')
        Assumptions.assumeTrue(fake?.trim() as boolean, 'POLYCALL_TEST_FAKE_ABI2 not set (fake ABI-2 library not built)')
        assertLoadError(probe(fake), fake, 'polycall_ffi_abi_version() returned 2')
    }
}
