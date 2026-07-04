import org.obinexus.polycall.Polycall
import org.obinexus.polycall.PolycallException

assert Polycall.runConfig('groovy-polycallrc') == 0

try {
    Polycall.runConfigOrThrow('__status_37__')
    assert false: 'runConfigOrThrow should propagate a non-zero core status'
} catch (PolycallException error) {
    assert error.status == 37
    assert error.configPath == '__status_37__'
}

println 'groovy-polycall Groovy/JNI smoke test: PASS'
