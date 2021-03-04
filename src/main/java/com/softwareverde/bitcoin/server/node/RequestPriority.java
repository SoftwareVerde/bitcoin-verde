package com.softwareverde.bitcoin.server.node;

public enum RequestPriority {
    NONE(0),
    NORMAL(100);

    private final int _priority;

    RequestPriority(final int priority) {
        _priority = priority;
    }

    public int getPriority() {
        return _priority;
    }
}
