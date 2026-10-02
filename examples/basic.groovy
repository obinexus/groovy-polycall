import org.obinexus.polycall.Peer
import org.obinexus.polycall.PeerMessage
import org.obinexus.polycall.Polycall

// Validate a configuration for running, then exchange one message between two nodes.
// Run with the binding jar and Groovy on the classpath and
// --enable-native-access=ALL-UNNAMED (POLYCALL_LIBRARY selects the library).
String configPath = args ? args[0] : Polycall.DEFAULT_CONFIG
println "libpolycall ${Polycall.version()} (binding ABI ${Polycall.abiVersion()}) from ${Polycall.libraryName()}"
Polycall.runConfigOrThrow(configPath)
println "'${configPath}' is valid for running with this build"

Peer.open('alpha', '127.0.0.1:0').withCloseable { Peer alpha ->
    Peer.open('beta', '127.0.0.1:0').withCloseable { Peer beta ->
        alpha.register('beta', beta.endpoint)
        alpha.send('beta', 'hello from Groovy', 'example-1', 5000)
        PeerMessage m = beta.recv(5000)
        println "beta received '${m.text}' from ${m.sender} (id ${m.messageId})"
    }
}
