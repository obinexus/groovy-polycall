package org.obinexus.polycall

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.obinexus.polycall.Fixtures.T
import static org.obinexus.polycall.Fixtures.TOKEN
import static org.obinexus.polycall.Fixtures.randomBytes

/**
 * Cross-language interop: the C CLI (`polycall peer serve/send/recv/health/
 * register`) and, when POLYCALL_INTEROP_ECHO names one, another binding's
 * echo agent. Every delivery is verified at the receiver.
 */
class InteropTest {
    @TempDir
    Path dir

    static byte[] mixed() {
        byte[] head = 'héllo — 世界 🌍 '.getBytes(StandardCharsets.UTF_8)
        byte[] all = (0..255).collect { it as byte } as byte[]
        byte[] out = new byte[head.length + all.length]
        System.arraycopy(head, 0, out, 0, head.length)
        System.arraycopy(all, 0, out, head.length, all.length)
        out
    }

    @Test
    void 'Groovy peer and C CLI peer exchange payloads both ways'() {
        String cli = Fixtures.requireCli().toString()
        byte[] payload = mixed()
        Fixtures.startCliPeer(dir, 'cnode').withCloseable { Fixtures.Proc cnode ->
            Peer.open('gnode', '127.0.0.1:0', TOKEN).withCloseable { Peer g ->
                g.register('cnode', cnode.endpoint)
                g.ping('cnode', T)
                g.send('cnode', payload, 'g2c-1', T)
                def r = Fixtures.run([cli, 'peer', 'recv', '--to', cnode.endpoint, '-t', '5000'], dir)
                assertEquals(0, r.exit, r.stderr)
                assertEquals('gnode', Fixtures.jsonString(r.out, 'from'))
                assertEquals('g2c-1', Fixtures.jsonString(r.out, 'id'))
                assertArrayEquals(payload, Base64.decoder.decode(Fixtures.jsonString(r.out, 'payload_b64')))

                byte[] big = randomBytes(3, Polycall.PEER_MAX_PAYLOAD)
                g.send('cnode', big, 'g2c-max', 20_000)
                r = Fixtures.run([cli, 'peer', 'recv', '--to', cnode.endpoint, '-t', '10000', '--raw'], dir)
                assertEquals(0, r.exit, r.stderr)
                assertArrayEquals(big, r.stdout, '1 MiB identical at the C node')

                Path f = dir.resolve('c2g.bin')
                Files.write(f, payload)
                r = Fixtures.run([cli, 'peer', 'send', '--from', 'cnode', '--to', g.endpoint, '--id', 'c2g-1',
                                  '--payload-file', f.toString()], dir)
                assertEquals(0, r.exit, r.stderr)
                PeerMessage m = g.recv(T)
                assertEquals('cnode', m.sender)
                assertEquals('c2g-1', m.messageId)
                assertArrayEquals(payload, m.payload)
            }
        }
    }

    @Test
    void 'C CLI manages a Groovy-hosted node over the wire'() {
        String cli = Fixtures.requireCli().toString()
        Peer.open('ghost', '127.0.0.1:0', TOKEN).withCloseable { Peer g ->
            def r = Fixtures.run([cli, 'peer', 'health', '--to', g.endpoint], dir)
            assertEquals(0, r.exit, r.stderr)
            assertTrue(r.out.contains('"node_id":"ghost"'), r.out)
            r = Fixtures.run([cli, 'peer', 'register', '--to', g.endpoint, '--id', 'remote1', '--peer-endpoint', '127.0.0.1:9'], dir)
            assertEquals(0, r.exit, r.stderr)
            assertTrue(g.list().contains('"remote1":"127.0.0.1:9"'), g.list())
            r = Fixtures.run([cli, 'peer', 'send', '--from', 'mallory', '--to', g.endpoint, '--payload', 'x'], dir,
                    ['POLYCALL_DEV_TOKEN': 'wrong-token'])
            assertEquals(7, r.exit, r.out + r.stderr)
            assertNull(g.tryRecv(300))
        }
    }

    /** Another binding's echo agent: AGENT peer echo --node-id ID ... --peer ORIGIN=H:P --count N. */
    @Test
    void "Groovy peer exchanges payloads with another binding's peer"() {
        String command = System.getenv('POLYCALL_INTEROP_ECHO')
        Assumptions.assumeTrue(command?.trim() as boolean, "POLYCALL_INTEROP_ECHO not set (no other binding's echo agent provided)")
        String label = System.getenv('POLYCALL_INTEROP_ECHO_NAME') ?: 'external'
        List<byte[]> payloads = [new byte[0], "hello from groovy to ${label}".toString().getBytes(StandardCharsets.UTF_8),
                                 mixed(), randomBytes(11, 200_000), randomBytes(12, Polycall.PEER_MAX_PAYLOAD)]
        Peer.open('groovy-origin', '127.0.0.1:0', TOKEN).withCloseable { Peer origin ->
            List<String> cmd = (command =~ /"([^"]*)"|(\S+)/).collect { List<String> g -> g[1] ?: g[2] } +
                    ['peer', 'echo', '--node-id', 'echo-agent', '--endpoint', '127.0.0.1:0',
                     '--peer', "groovy-origin=${origin.endpoint}".toString(), '--count', payloads.size().toString(),
                     '--idle-timeout-ms', '30000']
            Fixtures.start(dir, 'echo-agent', cmd).withCloseable { Fixtures.Proc agent ->
                origin.register('echo-agent', agent.endpoint)
                payloads.eachWithIndex { byte[] p, int i ->
                    origin.send('echo-agent', p, "x${i}".toString(), 20_000)
                    PeerMessage m = origin.recv(20_000)
                    assertEquals('echo-agent', m.sender, agent.log())
                    assertEquals("echo-x${i}".toString(), m.messageId)
                    assertArrayEquals(p, m.payload, "payload ${i} after the round trip".toString())
                }
                assertTrue(agent.process.waitFor(20, TimeUnit.SECONDS), agent.log())
                assertEquals(0, agent.process.exitValue(), agent.log())
                assertTrue(agent.log().contains('"from":"groovy-origin"'), agent.log())
            }
        }
        println "echo interop with ${label}: ${payloads.size()} round trips verified"
    }
}
