package org.obinexus.polycall

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.obinexus.polycall.Fixtures.expectStatus

/** Loading, version/ABI and run_config against the real installed library. */
class LibraryConfigTest {
    @TempDir
    Path dir

    private String write(String name, String text) {
        Path p = dir.resolve(name)
        Files.writeString(p, text)
        p.toString()
    }

    @Test
    void 'abi version is 1 and version is at least 1_1_0'() {
        assertEquals(1, Polycall.abiVersion())
        def (major, minor) = Polycall.version().tokenize('.').take(2).collect { it.takeWhile { Character.isDigit(it as char) } as int }
        assertTrue(major > 1 || (major == 1 && minor >= 1), Polycall.version())
        assertTrue(Polycall.coreVersion().length() > 0)
    }

    @Test
    void 'POLYCALL_LIBRARY is honoured'() {
        String env = System.getenv('POLYCALL_LIBRARY')
        Assumptions.assumeTrue(env?.trim() as boolean, 'POLYCALL_LIBRARY is not set for this run')
        assertEquals(env, Polycall.libraryName())
    }

    @Test
    void 'platform name is used without POLYCALL_LIBRARY'() {
        Assumptions.assumeFalse(System.getenv('POLYCALL_LIBRARY')?.trim() as boolean, 'POLYCALL_LIBRARY is set for this run')
        assertTrue(Polycall.libraryName() in NativeApi.defaultNames(), Polycall.libraryName())
    }

    @Test
    void 'strerror names statuses and last error is per thread'() {
        assertTrue(Polycall.strerror(Status.E_TIMEOUT).startsWith('POLYCALL_E_TIMEOUT:'))
        assertTrue(Polycall.strerror(Status.E_CLOSED).startsWith('POLYCALL_E_CLOSED:'))
        assertTrue(Polycall.strerror(-999).startsWith('POLYCALL_E_UNKNOWN'))
        assertEquals(Status.E_NOT_FOUND, Polycall.runConfig(dir.resolve('missing').toString()))
        String detail = Polycall.lastError()
        assertTrue(detail.length() > 0)
        String other = null
        Thread t = Thread.start { other = Polycall.lastError() }
        t.join()
        assertEquals('', other)
        assertEquals(detail, Polycall.lastError())
    }

    @Test
    void 'missing library and missing symbols are clear errors'() {
        String bogus = dir.resolve(Fixtures.WINDOWS ? 'no-such-polycall.dll' : 'libno-such.so.1').toString()
        def e1 = assertThrows(PolycallLoadException, { NativeApi.load(bogus) })
        assertTrue(e1.reason.startsWith('cannot load the library'), e1.message)
        String other = Fixtures.WINDOWS ? 'kernel32.dll' : 'libc.so.6'
        def e2 = assertThrows(PolycallLoadException, { NativeApi.load(other) })
        assertTrue(e2.reason.contains('missing symbol(s)') && e2.reason.contains('polycall_ffi_abi_version'), e2.message)
    }

    /** ABI mismatch, using a clearly-labelled FAKE library (src/test/c/fake_polycall_abi2.c). */
    @Test
    void 'abi mismatch is refused'() {
        String fake = System.getenv('POLYCALL_TEST_FAKE_ABI2')
        Assumptions.assumeTrue(fake?.trim() as boolean, 'POLYCALL_TEST_FAKE_ABI2 not set (fake ABI-2 library not built)')
        def e = assertThrows(PolycallLoadException, { NativeApi.load(fake) })
        assertTrue(e.reason.contains('returned 2'), e.message)
    }

    @Test
    void 'run_config valid, missing, invalid, strict and TLS'() {
        String valid = write('valid-polycallrc', 'log_level=info\nmax_connections=10\ntls_enabled=false\n')
        assertEquals(Status.OK, Polycall.runConfig(valid))
        Polycall.runConfigOrThrow(valid)
        Polycall.runConfigOrThrow(valid, false)
        expectStatus(Status.E_NOT_FOUND) { Polycall.runConfigOrThrow(dir.resolve('absent').toString()) }
        String invalid = write('invalid-polycallrc', 'max_connections=lots\n')
        assertTrue(expectStatus(Status.E_CONFIG) { Polycall.runConfigOrThrow(invalid, false) }.detail.contains('max_connections'))
        expectStatus(Status.E_CONFIG) { Polycall.runConfigOrThrow(write('garbage-polycallrc', 'not a config line\n')) }
        String unknown = write('unknown-polycallrc', 'log_level=info\nbogus_key=1\n')
        Polycall.runConfigOrThrow(unknown, false)
        assertTrue(expectStatus(Status.E_CONFIG) { Polycall.runConfigOrThrow(unknown) }.detail.contains('bogus_key'))
        String tls = write('tls-polycallrc', 'tls_enabled=true\ncert_file=/x/c.pem\nkey_file=/x/k.pem\n')
        Polycall.runConfigOrThrow(tls, false)
        assertTrue(expectStatus(Status.E_UNSUPPORTED) { Polycall.runConfigOrThrow(tls) }.detail.contains('tls_enabled'))
        assertEquals(Status.E_INVALID_ARGUMENT, Polycall.runConfig(''))
        expectStatus(Status.E_INVALID_ARGUMENT) { Polycall.runConfig((String) null) }
        expectStatus(Status.E_INVALID_ARGUMENT) { Polycall.runConfig('a\u0000b') }
    }

    @Test
    void 'UTF-8 path is passed intact'() {
        Polycall.runConfigOrThrow(write('café-世界-polycallrc', 'log_level=info\n'))
    }

    @Test
    void 'shipped configurations validate for running'() {
        ['groovy-polycallrc', 'examples/groovy-polycallrc'].each { String f ->
            Path p = Path.of(f).toAbsolutePath()
            assertTrue(Files.isRegularFile(p), "missing ${p}".toString())
            Polycall.runConfigOrThrow(p.toString())
        }
    }

    @Test
    void 'describe returns JSON'() {
        String json = Polycall.describe(write('describe-polycallrc', 'log_level=debug\nmax_connections=7\n'))
        assertTrue(json.startsWith('{') && json.endsWith('}') && json.contains('max_connections'), json)
    }
}
