import org.obinexus.polycall.Polycall

String configPath = args ? args[0] : Polycall.DEFAULT_CONFIG
Polycall.runConfigOrThrow(configPath)
println "libpolycall completed '${configPath}' successfully"
