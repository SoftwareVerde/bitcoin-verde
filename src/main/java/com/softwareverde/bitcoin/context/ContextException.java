package com.softwareverde.bitcoin.context;

public class ContextException extends Exception {
    public ContextException(final String message) {
        super(message);
    }

    public ContextException(final Exception baseException) {
        super(baseException);
    }

    public ContextException(final String message, final Exception baseException) {
        super(message, baseException);
    }
}
