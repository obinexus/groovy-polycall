package org.obinexus.polycall

import groovy.transform.CompileStatic

import java.util.Objects

/** Thin Groovy/JNI adapter for libpolycall 1.5. */
@CompileStatic
final class Polycall {
    static final String DEFAULT_CONFIG = 'groovy-polycallrc'
    static final String LIBRARY_PATH_PROPERTY = 'groovy.polycall.library'

    static {
        String explicitLibrary = System.getProperty(LIBRARY_PATH_PROPERTY)
        if (explicitLibrary) {
            System.load(new File(explicitLibrary).absolutePath)
        } else {
            System.loadLibrary('groovy_polycall')
        }
    }

    private Polycall() {}

    private static native int nativeRunConfig(String configPath)

    /** Run a libpolycall configuration and return the unchanged core status. */
    static int runConfig(String configPath = DEFAULT_CONFIG) {
        Objects.requireNonNull(configPath, 'configPath')
        return nativeRunConfig(configPath)
    }

    /** Run a configuration and throw when libpolycall reports a failure. */
    static void runConfigOrThrow(String configPath = DEFAULT_CONFIG) {
        int status = runConfig(configPath)
        if (status != 0) {
            throw new PolycallException(status, configPath)
        }
    }
}
