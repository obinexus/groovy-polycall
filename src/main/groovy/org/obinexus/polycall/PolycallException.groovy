package org.obinexus.polycall

import groovy.transform.CompileStatic

/** Binding ABI v1 status codes (polycall.h). Append-only. */
@CompileStatic
final class Status {
    private Status() {}

    static final int OK = 0
    static final int E_INVALID_ARGUMENT = -1
    static final int E_NO_MEMORY = -2
    static final int E_INVALID_HANDLE = -3
    static final int E_TIMEOUT = -4
    static final int E_TRANSPORT = -5
    static final int E_PROTOCOL = -6
    static final int E_NOT_FOUND = -7
    static final int E_AUTH = -8
    static final int E_REMOTE = -9
    static final int E_TOO_LARGE = -10
    static final int E_BUSY = -11
    static final int E_CANCELLED = -12
    static final int E_CONFIG = -13
    static final int E_ADDRESS_IN_USE = -14
    static final int E_UNSUPPORTED = -15
    static final int E_PERMISSION = -16
    static final int E_CLOSED = -17
    static final int E_INTERNAL = -18
}

/**
 * A Polycall call returned a negative status: the code, its name from
 * polycall_strerror() and this thread's detail from polycall_last_error().
 */
@CompileStatic
final class PolycallException extends RuntimeException {
    /** The negative POLYCALL_E_* code (see {@link Status}). */
    final int status
    /** The full polycall_strerror() text. */
    final String statusText
    /** polycall_last_error() detail (may be empty). */
    final String detail
    /** For Polycall.call: the runtime's error object {"code":..,"message":..}, else null. */
    final String remoteError
    /** For E_TOO_LARGE from a caller buffer: the size the library needs, else -1. */
    final long requiredSize

    PolycallException(int status, String statusText, String detail, String context,
                      String remoteError = null, long requiredSize = -1L) {
        super(format(status, statusText, detail, context))
        this.status = status
        this.statusText = statusText
        this.detail = detail ?: ''
        this.remoteError = remoteError
        this.requiredSize = requiredSize
    }

    /** The status name, e.g. POLYCALL_E_TIMEOUT. */
    String getStatusName() {
        nameOf(statusText)
    }

    private static String nameOf(String text) {
        int colon = text.indexOf(':')
        colon > 0 ? text.substring(0, colon) : text
    }

    private static String format(int status, String text, String detail, String context) {
        StringBuilder sb = new StringBuilder()
        if (context) sb.append(context).append(': ')
        sb.append(nameOf(text)).append(' (').append(status).append(')')
        if (detail) sb.append(': ').append(detail)
        sb.toString()
    }
}
