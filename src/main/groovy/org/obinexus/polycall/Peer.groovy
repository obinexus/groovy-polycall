package org.obinexus.polycall

import groovy.transform.CompileStatic

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.ref.Cleaner
import java.lang.ref.Reference
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

import static java.lang.foreign.ValueLayout.JAVA_BYTE
import static java.lang.foreign.ValueLayout.JAVA_INT
import static java.lang.foreign.ValueLayout.JAVA_LONG

/** One message taken from a peer node's inbox (binary-safe payload). */
@CompileStatic
final class PeerMessage {
    /** The sending node's id. */
    final String sender
    /** The message id (de-duplication key together with the sender). */
    final String messageId
    private final byte[] bytes

    PeerMessage(String sender, String messageId, byte[] payload) {
        this.sender = sender
        this.messageId = messageId
        this.bytes = payload.clone()
    }

    /** A copy of the payload bytes. */
    byte[] getPayload() { bytes.clone() }

    int getLength() { bytes.length }

    /** The payload decoded as UTF-8. */
    String getText() { new String(bytes, StandardCharsets.UTF_8) }

    @Override
    boolean equals(Object o) {
        o instanceof PeerMessage && sender == ((PeerMessage) o).sender &&
                messageId == ((PeerMessage) o).messageId && Arrays.equals(bytes, ((PeerMessage) o).bytes)
    }

    @Override
    int hashCode() { Objects.hash(sender, messageId, Arrays.hashCode(bytes)) }

    @Override
    String toString() { "PeerMessage(from=${sender}, id=${messageId}, ${bytes.length} bytes)" }
}

/**
 * A Polycall peer node (polycall_peer_*) with its own registry and inbox,
 * exchanging payloads directly with other nodes in any language/process.
 * Thread-safe; close() is idempotent and wakes blocked receivers; a closed
 * peer's stale handle is reported by the library as E_INVALID_HANDLE. A peer
 * that is never closed is closed by a Cleaner once unreachable; every method
 * keeps the peer reachable until its native call has returned, so the Cleaner
 * can never close a handle that is still in use.
 */
@CompileStatic
final class Peer implements AutoCloseable {
    /** UINT32_MAX: block in recv until a message, cancel or close. */
    static final long WAIT_FOREVER = 0xFFFF_FFFFL
    private static final long INITIAL_RECV_CAPACITY = 64L * 1024L
    private static final Cleaner CLEANER = Cleaner.create()

    /** The library's integer handle (> 0). */
    final int handle
    private final AtomicBoolean closed = new AtomicBoolean()
    private final CloseAction closeAction
    private final Cleaner.Cleanable cleanable

    private static final class CloseAction implements Runnable {
        private final int handle
        volatile int status = Status.OK

        CloseAction(int handle) { this.handle = handle }

        @Override
        void run() {
            try {
                status = Polycall.invokeInt(Polycall.api().peerClose, handle)
            } catch (Throwable ignored) {
                status = Status.E_INTERNAL
            }
        }
    }

    private Peer(int handle) {
        this.handle = handle
        this.closeAction = new CloseAction(handle)
        this.cleanable = CLEANER.register(this, closeAction)
    }

    /**
     * Open a node (polycall_peer_open). bindEndpoint "127.0.0.1:0" = ephemeral
     * port, null = send-only; authToken null/"" = no authentication.
     */
    static Peer open(String nodeId, String bindEndpoint, String authToken = null) {
        Arena arena = Arena.ofConfined()
        try {
            MemorySegment out = arena.allocate(JAVA_INT)
            int st = Polycall.invokeInt(Polycall.api().peerOpen,
                    Polycall.cstr(arena, nodeId, 'nodeId'),
                    Polycall.cstrOrNull(arena, bindEndpoint, 'bindEndpoint'),
                    Polycall.cstrOrNull(arena, authToken, 'authToken'), out)
            if (st != Status.OK) throw Polycall.error(st, "peer_open(${nodeId}, ${bindEndpoint})".toString())
            return new Peer(out.get(JAVA_INT, 0L))
        } finally {
            arena.close()
        }
    }

    /** Close a raw handle; unknown, closed or stale handles raise E_INVALID_HANDLE. */
    static void closeHandle(int handle) {
        Polycall.check(Polycall.invokeInt(Polycall.api().peerClose, handle), "peer_close(${handle})".toString())
    }

    /** True once close() was called on this object. */
    boolean isClosed() { closed.get() }

    /** The bound "host:port" ("" for a send-only node). */
    String getEndpoint() {
        int h = handle
        try {
            return Polycall.text('peer_endpoint') { MemorySegment buf, long cap, MemorySegment len ->
                Polycall.invokeInt(Polycall.api().peerEndpoint, h, buf, cap)
            }
        } finally {
            Reference.reachabilityFence(this)
        }
    }

    /** This node's id. */
    String getNodeId() {
        int h = handle
        try {
            return Polycall.text('peer_node_id') { MemorySegment buf, long cap, MemorySegment len ->
                Polycall.invokeInt(Polycall.api().peerNodeId, h, buf, cap)
            }
        } finally {
            Reference.reachabilityFence(this)
        }
    }

    /** Add or replace peerId -> endpoint in THIS node's registry. */
    void register(String peerId, String endpoint) {
        Arena arena = Arena.ofConfined()
        try {
            Polycall.check(Polycall.invokeInt(Polycall.api().peerRegister, handle,
                    Polycall.cstr(arena, peerId, 'peerId'), Polycall.cstr(arena, endpoint, 'endpoint')),
                    "peer_register(${peerId})".toString())
        } finally {
            arena.close()
            Reference.reachabilityFence(this)
        }
    }

    /** Remove peerId; E_NOT_FOUND when it is not registered. */
    void unregister(String peerId) {
        Arena arena = Arena.ofConfined()
        try {
            Polycall.check(Polycall.invokeInt(Polycall.api().peerUnregister, handle,
                    Polycall.cstr(arena, peerId, 'peerId')), "peer_unregister(${peerId})".toString())
        } finally {
            arena.close()
            Reference.reachabilityFence(this)
        }
    }

    /** THIS node's registry as JSON {"id":"host:port",...}. */
    String list() {
        int h = handle
        try {
            return Polycall.text('peer_list') { MemorySegment buf, long cap, MemorySegment len ->
                Polycall.invokeInt(Polycall.api().peerList, h, buf, cap, len)
            }
        } finally {
            Reference.reachabilityFence(this)
        }
    }

    /** This node's health as JSON. */
    String health() {
        int h = handle
        try {
            return Polycall.text('peer_health') { MemorySegment buf, long cap, MemorySegment len ->
                Polycall.invokeInt(Polycall.api().peerHealth, h, buf, cap, len)
            }
        } finally {
            Reference.reachabilityFence(this)
        }
    }

    /** GET /health of peer; OK only when healthy and, for a registered id, answering as that id. */
    void ping(String peer, long timeoutMs) {
        Arena arena = Arena.ofConfined()
        try {
            Polycall.check(Polycall.invokeInt(Polycall.api().peerPing, handle, Polycall.cstr(arena, peer, 'peer'),
                    Polycall.uint32(timeoutMs, 'timeoutMs')), "peer_ping(${peer})".toString())
        } finally {
            arena.close()
            Reference.reachabilityFence(this)
        }
    }

    /**
     * Deliver payload (binary-safe, <= 1 MiB) to peer (registered id or
     * "host:port"). Exactly one attempt; returning means the receiver stored
     * and acknowledged it. messageId null = generated; on E_TIMEOUT retry
     * with the SAME id.
     */
    void send(String peer, byte[] payload, String messageId, long timeoutMs) {
        if (payload == null) throw Polycall.argumentError('payload is null')
        Arena arena = Arena.ofConfined()
        try {
            MemorySegment p = Polycall.cstr(arena, peer, 'peer')
            MemorySegment mid = Polycall.cstrOrNull(arena, messageId, 'messageId')
            MemorySegment data = MemorySegment.NULL
            if (payload.length > 0) {
                data = arena.allocate((long) payload.length)
                MemorySegment.copy(payload, 0, data, JAVA_BYTE, 0L, payload.length)
            }
            int st = Polycall.invokeInt(Polycall.api().peerSend, handle, p, data, (long) payload.length, mid,
                    Polycall.uint32(timeoutMs, 'timeoutMs'))
            Polycall.check(st, "peer_send(${peer}, ${payload.length} bytes, id=${messageId})".toString())
        } finally {
            arena.close()
            Reference.reachabilityFence(this)
        }
    }

    /** Send UTF-8 text. */
    void send(String peer, String text, String messageId, long timeoutMs) {
        if (text == null) throw Polycall.argumentError('text is null')
        send(peer, text.getBytes(StandardCharsets.UTF_8), messageId, timeoutMs)
    }

    /**
     * Take the oldest message into a payload buffer of exactly payloadCapacity
     * bytes; a larger message raises E_TOO_LARGE with requiredSize and stays queued.
     */
    PeerMessage recv(long timeoutMs, long payloadCapacity) {
        int timeout = Polycall.uint32(timeoutMs, 'timeoutMs')
        if (payloadCapacity < 0L || payloadCapacity > Integer.MAX_VALUE) {
            throw Polycall.argumentError("payloadCapacity out of range: ${payloadCapacity}".toString())
        }
        Arena arena = Arena.ofConfined()
        try {
            MemorySegment sender = arena.allocate(Polycall.ID_MAX)
            MemorySegment mid = arena.allocate(Polycall.ID_MAX)
            MemorySegment payload = payloadCapacity == 0L ? MemorySegment.NULL : arena.allocate(payloadCapacity)
            MemorySegment len = arena.allocate(JAVA_LONG)
            int st = Polycall.invokeInt(Polycall.api().peerRecv, handle, timeout,
                    sender, Polycall.ID_MAX, mid, Polycall.ID_MAX, payload, payloadCapacity, len)
            long n = len.get(JAVA_LONG, 0L)
            if (st == Status.E_TOO_LARGE) throw Polycall.error(st, 'peer_recv', null, n)
            Polycall.check(st, 'peer_recv')
            byte[] data = n == 0L ? new byte[0] : payload.asSlice(0L, n).toArray(JAVA_BYTE)
            return new PeerMessage(sender.getString(0L), mid.getString(0L), data)
        } finally {
            arena.close()
            Reference.reachabilityFence(this)
        }
    }

    /**
     * Take the oldest message, waiting up to timeoutMs (0 = poll,
     * WAIT_FOREVER = until a message, cancel or close); the buffer grows to
     * whatever the message needs.
     */
    PeerMessage recv(long timeoutMs) {
        Polycall.uint32(timeoutMs, 'timeoutMs')
        long deadline = timeoutMs == WAIT_FOREVER ? Long.MAX_VALUE : System.nanoTime() + timeoutMs * 1_000_000L
        long capacity = INITIAL_RECV_CAPACITY
        long wait = timeoutMs
        while (true) {
            try {
                return recv(wait, capacity)
            } catch (PolycallException e) {
                if (e.status != Status.E_TOO_LARGE || e.requiredSize <= capacity) throw e
                capacity = e.requiredSize // the message stayed queued: retry with a big enough buffer
                if (deadline != Long.MAX_VALUE) wait = Math.max(0L, (long) ((deadline - System.nanoTime()) / 1_000_000L))
            }
        }
    }

    /** Like recv but null instead of E_TIMEOUT. */
    PeerMessage tryRecv(long timeoutMs) {
        try {
            return recv(timeoutMs)
        } catch (PolycallException e) {
            if (e.status == Status.E_TIMEOUT) return null
            throw e
        }
    }

    /** Wake every recv blocked on this node with E_CANCELLED. */
    void cancel() {
        try {
            Polycall.check(Polycall.invokeInt(Polycall.api().peerCancel, handle), 'peer_cancel')
        } finally {
            Reference.reachabilityFence(this)
        }
    }

    /** Stop the listener, wake blocked receivers (E_CLOSED), release the node. Idempotent. */
    @Override
    void close() {
        if (!closed.compareAndSet(false, true)) return
        cleanable.clean()
        int st = closeAction.status
        if (st != Status.OK) throw Polycall.error(st, "peer_close(${handle})".toString())
    }

    @Override
    String toString() { "Peer(handle=${handle}${closed.get() ? ', closed' : ''})" }
}
