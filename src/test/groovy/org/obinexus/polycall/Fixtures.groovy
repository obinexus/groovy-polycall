package org.obinexus.polycall

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.function.Executable

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.fail

/** Real-process fixtures: the installed polycall CLI, runtimes and peer nodes. */
class Fixtures {
    /** Random shared token for this test run (never a real secret). */
    static final String TOKEN = 'qa-token-' + UUID.randomUUID()
    static final long T = 5000L
    static final boolean WINDOWS = System.getProperty('os.name', '').toLowerCase().contains('win')

    /** The polycall CLI: POLYCALL_CLI, else polycall(.exe) on PATH. */
    static Path cli() {
        String explicit = System.getenv('POLYCALL_CLI')
        if (explicit?.trim()) {
            Path p = Path.of(explicit)
            return Files.isRegularFile(p) ? p : null
        }
        String exe = WINDOWS ? 'polycall.exe' : 'polycall'
        (System.getenv('PATH') ?: '').split(File.pathSeparator).findAll { it }.collect { Path.of(it, exe) }
                .find { Files.isRegularFile(it) }
    }

    /** The CLI, or abort the test as SKIPPED (never passed) when it is absent. */
    static Path requireCli() {
        Path c = cli()
        Assumptions.assumeTrue(c != null, 'polycall CLI not found (set POLYCALL_CLI or put polycall on PATH)')
        c
    }

    private static void environment(ProcessBuilder pb, Map<String, String> extra = [:]) {
        pb.environment().put('POLYCALL_DEV_TOKEN', TOKEN)
        pb.environment().put('POLYCALL_TELEMETRY', 'off')
        extra.each { k, v -> v == null ? pb.environment().remove(k) : pb.environment().put(k, v) }
    }

    static class Proc implements AutoCloseable {
        final Process process
        final Path logFile
        final String endpoint

        Proc(Process process, Path logFile, String endpoint) {
            this.process = process
            this.logFile = logFile
            this.endpoint = endpoint
        }

        String log() {
            try { Files.readString(logFile) } catch (IOException e) { "<no log: ${e}>" }
        }

        @Override
        void close() {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }

    static String waitForLine(Path file, Process owner, long limitMs) {
        long end = System.nanoTime() + limitMs * 1_000_000L
        while (System.nanoTime() < end) {
            if (Files.exists(file)) {
                String s = Files.readString(file)
                if (s.trim() && (s.endsWith('\n') || s.trim() ==~ /.+:\d+/)) return s.trim()
            }
            if (!owner.alive) break
            Thread.sleep(50)
        }
        null
    }

    static Proc start(Path dir, String name, List<String> cmd) {
        Path epFile = dir.resolve(name + '.ep')
        Path log = dir.resolve(name + '.log')
        ProcessBuilder pb = new ProcessBuilder(cmd + ['--endpoint-file', epFile.toString()])
                .directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile())
        environment(pb)
        Process p = pb.start()
        String ep = waitForLine(epFile, p, 20_000)
        if (ep == null) {
            p.destroyForcibly()
            fail("${name} did not report an endpoint: ${Files.readString(log)}".toString())
        }
        new Proc(p, log, ep)
    }

    static Proc startRuntime(Path dir) {
        start(dir, 'runtime', [requireCli().toString(), 'start', '--endpoint', '127.0.0.1:0'])
    }

    static Proc startCliPeer(Path dir, String nodeId) {
        start(dir, "peer-${nodeId}".toString(),
                [requireCli().toString(), 'peer', 'serve', '--node-id', nodeId, '--endpoint', '127.0.0.1:0'])
    }

    static class Result {
        int exit
        byte[] stdout
        String stderr

        String getOut() { new String(stdout, 'UTF-8') }
    }

    static Result run(List<String> cmd, Path dir, Map<String, String> extra = [:], long limitMs = 30_000L) {
        Path err = Files.createTempFile(dir, 'stderr', '.txt')
        Path out = Files.createTempFile(dir, 'stdout', '.bin')
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile())
                .redirectError(err.toFile()).redirectOutput(out.toFile())
        environment(pb, extra)
        Process p = pb.start()
        p.outputStream.close()
        if (!p.waitFor(limitMs, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            fail("timed out: ${cmd}".toString())
        }
        new Result(exit: p.exitValue(), stdout: Files.readAllBytes(out), stderr: Files.readString(err))
    }

    static String jsonString(String json, String key) {
        def m = json =~ ('"' + java.util.regex.Pattern.quote(key) + '":"((?:[^"\\\\]|\\\\.)*)"')
        m.find() ? m.group(1) : null
    }

    static PolycallException expectStatus(int status, Closure action) {
        PolycallException e = assertThrows(PolycallException, { action.call() } as Executable)
        assertEquals(status, e.status, e.message)
        assertEquals(Polycall.strerror(status).split(':')[0], e.statusName, e.message)
        e
    }

    static int freePort() {
        new ServerSocket(0, 1, InetAddress.getByName('127.0.0.1')).withCloseable { it.localPort }
    }

    static byte[] randomBytes(long seed, int n) {
        byte[] b = new byte[n]
        new Random(seed).nextBytes(b)
        b
    }
}
