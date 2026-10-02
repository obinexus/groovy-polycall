package org.obinexus.polycall

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Future

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.obinexus.polycall.Fixtures.expectStatus

/** polycall_call against a real `polycall start` runtime and `polycall daemon start`. */
class CallTest {
    static Path dir
    static Fixtures.Proc runtime

    static String getEp() { runtime.endpoint }

    @BeforeAll
    static void start() {
        dir = Files.createTempDirectory('groovy-polycall-call')
        runtime = Fixtures.startRuntime(dir)
    }

    @AfterAll
    static void stop() {
        runtime?.close()
        dir?.toFile()?.deleteDir()
    }

    @Test
    void 'success, UTF-8 input and null input'() {
        String out = Polycall.call(ep, 'inventory', 'get', '{"item_id":"widget-a"}', 5000)
        assertTrue(out.contains('"quantity":42'), out)
        String input = '{"text":"héllo — 世界 🌍"}'
        assertEquals('{"echo":' + input + '}', Polycall.call(ep, 'debug', 'echo', input, 5000))
        assertEquals('{"echo":null}', Polycall.call(ep, 'debug', 'echo', null, 5000))
    }

    @Test
    void 'unknown operation, operation error and deadline'() {
        assertTrue(expectStatus(Status.E_NOT_FOUND) { Polycall.call(ep, 'inventory', 'nope', '{}', 5000) }.remoteError.contains('operation.unknown'))
        assertTrue(expectStatus(Status.E_REMOTE) { Polycall.call(ep, 'inventory', 'get', '{"item_id":"nope"}', 5000) }.remoteError.contains('item.unknown'))
        long t0 = System.nanoTime()
        def e = expectStatus(Status.E_TIMEOUT) { Polycall.call(ep, 'debug', 'sleep', '{"ms":5000}', 300) }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 4000)
        assertNotNull(e.remoteError)
    }

    @Test
    void 'invalid input and no runtime'() {
        expectStatus(Status.E_INVALID_ARGUMENT) { Polycall.call(ep, 'debug', 'echo', '{not json', 5000) }
        expectStatus(Status.E_INVALID_ARGUMENT) { Polycall.call(ep, 'debug', 'echo', '{}', 0) }
        expectStatus(Status.E_INVALID_ARGUMENT) { Polycall.call(ep, 'debug', 'echo', '{}', 600_001) }
        expectStatus(Status.E_INVALID_ARGUMENT) { Polycall.call('no-port', 'debug', 'echo', '{}', 5000) }
        expectStatus(Status.E_TRANSPORT) { Polycall.call("127.0.0.1:${Fixtures.freePort()}".toString(), 'inventory', 'get', '{}', 2000) }
    }

    @Test
    void 'concurrent calls'() {
        def pool = Executors.newFixedThreadPool(8)
        try {
            List<Future<String>> futures = (0..<64).collect { int n ->
                pool.submit({ Polycall.call(ep, 'debug', 'echo', "{\"n\":${n}}".toString(), 10_000) } as java.util.concurrent.Callable<String>)
            }
            futures.eachWithIndex { Future<String> f, int n -> assertEquals("{\"echo\":{\"n\":${n}}}".toString(), f.get()) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    void 'call through polycall daemon'(@TempDir Path project) {
        String cli = Fixtures.requireCli().toString()
        Path file = project.resolve('Polycallfile')
        Files.writeString(file, 'server node 8080:8084\nnetwork start\ndaemon_endpoint=127.0.0.1:0\nauth_token_env=POLYCALL_DEV_TOKEN\n')
        def start = Fixtures.run([cli, '--format', 'json', 'daemon', 'start', '-t', '15000', file.toString()], project)
        try {
            assertEquals(0, start.exit, start.out + start.stderr)
            String endpoint = Fixtures.jsonString(start.out, 'endpoint')
            assertNotNull(endpoint, start.out)
            assertTrue(Polycall.call(endpoint, 'inventory', 'get', '{"item_id":"widget-b"}', 5000).contains('"quantity":7'))
        } finally {
            def stop = Fixtures.run([cli, 'daemon', 'stop', '-t', '10000', file.toString()], project)
            assertEquals(0, stop.exit, stop.out + stop.stderr)
        }
    }
}
