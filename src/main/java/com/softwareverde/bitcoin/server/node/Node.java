package com.softwareverde.bitcoin.server.node;

public class Node {
    protected static final Object NODE_ID_MUTEX = new Object();
    protected static Long _nextId = 0L;

    protected Long _id;

    public Node() {
        synchronized (NODE_ID_MUTEX) {
            _id = _nextId;
            _nextId += 1;
        }
    }

    public Long getId() { return _id; }
}
