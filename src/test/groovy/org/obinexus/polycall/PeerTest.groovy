package org.obinexus.polycall

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotEquals
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.obinexus.polycall.Fixtures.T
import static org.obinexus.polycall.Fixtures.TOKEN
import static org.obinexus.polycall.Fixtures.expectStatus
import static org.obinexus.polycall.Fixtures.randomBytes

/** Two (or more) real peer nodes in this JVM exchanging payloads over loopback. */
class PeerTest {
    Peer alpha
    Peer beta

    @BeforeEach
    void open() {
        alpha = Peer.open('alpha', '127.0.0.1:0', TOKEN)
        beta = Peer.open('beta', '127.0.0.1:0', TOKEN)
        alpha.register('beta', beta.endpoint)
        beta.register('alpha', alpha.endpoint)
    }

    @AfterEach
    void close() {
        alpha.close()
        beta.close()
    }

    private static byte[] utf8(String s) { s.getBytes(StandardCharsets.UTF_8) }

    private static void expectMessage(PeerMessage m, String sender, String id, byte[] payload) {
        assertEquals(sender, m.sender, 'sender id')
        assertEquals(id, m.messageId, 'message id')
        assertArrayEquals(payload, m.payload, 'payload bytes')
    }

    @Test
    void 'node id, endpoint and handle'() {
        assertEquals('alpha', alpha.nodeId)
        assertTrue(alpha.endpoint ==~ /127\.0\.0\.1:\d+/, alpha.endpoint)
        assertNotEquals(alpha.endpoint, beta.endpoint)
        assertTrue(alpha.handle > 0)
    }

    @Test
    void 'payloads in both directions verified at the receiver'() {
        alpha.send('beta', 'hello beta', 'm-a2b', T)
        expectMessage(beta.recv(T), 'alpha', 'm-a2b', utf8('hello beta'))
        beta.send('alpha', 'hello alpha', 'm-b2a', T)
        expectMessage(alpha.recv(T), 'beta', 'm-b2a', utf8('hello alpha'))
    }

    @Test
    void 'empty, UTF-8 and binary payloads'() {
        alpha.send('beta', new byte[0], 'm-empty', T)
        expectMessage(beta.recv(T), 'alpha', 'm-empty', new byte[0])
        String text = 'héllo — 世界 🌍'
        alpha.send('beta', text, 'm-utf8', T)
        PeerMessage m = beta.recv(T)
        expectMessage(m, 'alpha', 'm-utf8', utf8(text))
        assertEquals(text, m.text)
        byte[] all = (0..255).collect { it as byte } as byte[]
        beta.send('alpha', all, 'm-bin', T)
        expectMessage(alpha.recv(T), 'beta', 'm-bin', all)
    }

    @Test
    void 'exactly 1 MiB and 1 MiB + 1'() {
        byte[] big = randomBytes(42, Polycall.PEER_MAX_PAYLOAD)
        alpha.send('beta', big, 'm-max', 20_000)
        expectMessage(beta.recv(20_000), 'alpha', 'm-max', big)
        expectStatus(Status.E_TOO_LARGE) { alpha.send('beta', new byte[Polycall.PEER_MAX_PAYLOAD + 1], 'm-over', T) }
        assertNull(beta.tryRecv(300), 'nothing may arrive')
    }

    @Test
    void 'registry belongs to each node and changes only explicitly'() {
        Peer.open('carol', '127.0.0.1:0', TOKEN).withCloseable { Peer c ->
            Peer.open('dave', '127.0.0.1:0', TOKEN).withCloseable { Peer d ->
                assertEquals('{}', c.list())
                c.register('dave', d.endpoint)
                assertEquals("{\"dave\":\"${d.endpoint}\"}".toString(), c.list())
                assertEquals('{}', d.list(), 'registering on carol must not touch dave')
                c.send('dave', 'hi', 'm-reg', T)
                expectMessage(d.recv(T), 'carol', 'm-reg', utf8('hi'))
                assertEquals('{}', d.list(), 'receiving never registers the sender')
                expectStatus(Status.E_NOT_FOUND) { d.send('carol', 'back', 'm-x', T) }
                c.unregister('dave')
                expectStatus(Status.E_NOT_FOUND) { c.unregister('dave') }
                expectStatus(Status.E_NOT_FOUND) { c.send('dave', 'x', 'm-y', T) }
            }
        }
    }

    @Test
    void 'duplicate message id is delivered once'() {
        alpha.send('beta', 'once', 'm-dup', T)
        alpha.send('beta', 'once', 'm-dup', T)
        expectMessage(beta.recv(T), 'alpha', 'm-dup', utf8('once'))
        assertNull(beta.tryRecv(500))
        assertTrue(beta.health().contains('"duplicates":1'), beta.health())
    }

    @Test
    void 'wrong or missing token is an auth failure'() {
        Peer.open('mallory', null, 'wrong-token').withCloseable { Peer m ->
            Peer.open('anon', null, null).withCloseable { Peer a ->
                expectStatus(Status.E_AUTH) { m.send(beta.endpoint, 'x', 'm-auth1', T) }
                expectStatus(Status.E_AUTH) { a.send(beta.endpoint, 'x', 'm-auth2', T) }
                assertNull(beta.tryRecv(300))
                a.ping(beta.endpoint, T)
            }
        }
    }

    @Test
    void 'dead peer is transport, receive timeout'() {
        Peer dead = Peer.open('dead', '127.0.0.1:0', TOKEN)
        String ep = dead.endpoint
        dead.close()
        expectStatus(Status.E_TRANSPORT) { alpha.send(ep, 'into the void', 'm-dead', 3000) }
        long t0 = System.nanoTime()
        expectStatus(Status.E_TIMEOUT) { beta.recv(250) }
        long ms = (System.nanoTime() - t0).intdiv(1_000_000L) as long
        assertTrue(ms >= 200 && ms < 5000, "waited ${ms} ms".toString())
        expectStatus(Status.E_TIMEOUT) { beta.recv(0) }
    }

    @Test
    void 'too-small buffer leaves the message queued'() {
        byte[] hundred = randomBytes(7, 100)
        alpha.send('beta', hundred, 'm-small', T)
        assertEquals(100L, expectStatus(Status.E_TOO_LARGE) { beta.recv(T, 10L) }.requiredSize)
        assertEquals(100L, expectStatus(Status.E_TOO_LARGE) { beta.recv(T, 0L) }.requiredSize)
        expectMessage(beta.recv(T, 100L), 'alpha', 'm-small', hundred)
        byte[] large = randomBytes(8, 300_000)
        alpha.send('beta', large, 'm-large', T)
        expectMessage(beta.recv(T), 'alpha', 'm-large', large)
    }

    @Test
    void 'cancel and close wake a blocked receive'() {
        def seen = new AtomicReference<Throwable>()
        Thread t = Thread.start { try { beta.recv(Peer.WAIT_FOREVER) } catch (Throwable x) { seen.set(x) } }
        Thread.sleep(400)
        beta.cancel()
        t.join(5000)
        assertFalse(t.alive)
        assertEquals(Status.E_CANCELLED, ((PolycallException) seen.get()).status)
        alpha.send('beta', 'after cancel', 'm-after', T)
        expectMessage(beta.recv(T), 'alpha', 'm-after', utf8('after cancel'))

        Peer c = Peer.open('closer', '127.0.0.1:0', TOKEN)
        def seen2 = new AtomicReference<Throwable>()
        Thread t2 = Thread.start { try { c.recv(Peer.WAIT_FOREVER) } catch (Throwable x) { seen2.set(x) } }
        Thread.sleep(400)
        c.close()
        t2.join(5000)
        assertFalse(t2.alive)
        assertEquals(Status.E_CLOSED, ((PolycallException) seen2.get()).status)
    }

    @Test
    void 'double close, calls after close and invalid handles'() {
        Peer c = Peer.open('gone', '127.0.0.1:0', TOKEN)
        int h = c.handle
        c.close()
        c.close()
        assertTrue(c.isClosed())
        expectStatus(Status.E_INVALID_HANDLE) { Peer.closeHandle(h) }
        expectStatus(Status.E_INVALID_HANDLE) { c.endpoint }
        expectStatus(Status.E_INVALID_HANDLE) { c.list() }
        expectStatus(Status.E_INVALID_HANDLE) { c.health() }
        expectStatus(Status.E_INVALID_HANDLE) { c.cancel() }
        expectStatus(Status.E_INVALID_HANDLE) { c.register('x', '127.0.0.1:1') }
        expectStatus(Status.E_INVALID_HANDLE) { c.send(beta.endpoint, 'x', 'm-closed', T) }
        expectStatus(Status.E_INVALID_HANDLE) { c.recv(0) }
        [0, -1, Integer.MAX_VALUE, Integer.MIN_VALUE].each { int bogus -> expectStatus(Status.E_INVALID_HANDLE) { Peer.closeHandle(bogus) } }
        Peer.open('fresh', '127.0.0.1:0', TOKEN).withCloseable { Peer e ->
            assertNotEquals(h, e.handle)
            expectStatus(Status.E_INVALID_HANDLE) { c.nodeId }
        }
    }

    @Test
    void 'concurrent senders'() {
        List<Peer> senders = (0..<4).collect { int s ->
            Peer p = Peer.open("sender-${s}".toString(), null, TOKEN)
            p.register('beta', beta.endpoint)
            p
        }
        def pool = Executors.newFixedThreadPool(8)
        try {
            def futures = []
            senders.eachWithIndex { Peer p, int s ->
                (0..<2).each { int t ->
                    futures << pool.submit({
                        (0..<20).each { int i ->
                            String id = "s${s}-t${t}-${i}".toString()
                            p.send('beta', id, id, 10_000)
                        }
                    } as Runnable)
                }
            }
            futures.each { it.get(60, TimeUnit.SECONDS) }
            Set<String> seen = ConcurrentHashMap.newKeySet()
            160.times {
                PeerMessage m = beta.recv(T)
                assertEquals(m.messageId, m.text)
                assertTrue(seen.add("${m.sender}/${m.messageId}".toString()), "duplicate ${m}".toString())
            }
            assertEquals(160, seen.size())
            assertNull(beta.tryRecv(200))
        } finally {
            pool.shutdownNow()
            senders*.close()
        }
    }

    @Test
    void 'ping checks identity, invalid arguments and bind rules'() {
        alpha.ping('beta', T)
        alpha.register('gamma', beta.endpoint)
        assertTrue(expectStatus(Status.E_PROTOCOL) { alpha.ping('gamma', T) }.detail.contains('beta'))
        assertTrue(beta.health().contains('"node_id":"beta"'))
        expectStatus(Status.E_INVALID_ARGUMENT) { Peer.open('bad id!', '127.0.0.1:0', TOKEN) }
        expectStatus(Status.E_INVALID_ARGUMENT) { Peer.open('ok', 'not-an-endpoint', TOKEN) }
        expectStatus(Status.E_INVALID_ARGUMENT) { alpha.send('beta', 'x', 'bad id!', T) }
        expectStatus(Status.E_INVALID_ARGUMENT) { alpha.recv(-1) }
        expectStatus(Status.E_CONFIG) { Peer.open('exposed', '0.0.0.0:0', null) }
        expectStatus(Status.E_ADDRESS_IN_USE) { Peer.open('clash', beta.endpoint, TOKEN) }
        def e = expectStatus(Status.E_NOT_FOUND) { alpha.send('nobody', 'x', 'm-1', T) }
        assertEquals('POLYCALL_E_NOT_FOUND', e.statusName)
        assertTrue(e.detail.contains('nobody') && e.message.contains('POLYCALL_E_NOT_FOUND (-7)'))
        Peer.open('send-only', null, TOKEN).withCloseable { Peer s ->
            assertEquals('', s.endpoint)
            s.send(beta.endpoint, 'so', 'm-so', T)
            expectMessage(beta.recv(T), 'send-only', 'm-so', utf8('so'))
        }
    }
}
