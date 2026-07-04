package org.obinexus.polycall

import groovy.transform.CompileStatic

/** Raised by Polycall.runConfigOrThrow when the core returns a failure. */
@CompileStatic
final class PolycallException extends RuntimeException {
    final int status
    final String configPath

    PolycallException(int status, String configPath) {
        super("libpolycall failed with status ${status} for config '${configPath}'")
        this.status = status
        this.configPath = configPath
    }
}
