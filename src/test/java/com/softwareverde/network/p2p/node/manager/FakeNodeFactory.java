package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.network.p2p.node.NodeFactory;

public class FakeNodeFactory implements NodeFactory<FakeNode> {
    protected final ThreadPool _threadPool;

    public FakeNodeFactory(final ThreadPool threadPool) {
        _threadPool = threadPool;
    }

    @Override
    public FakeNode newNode(final String host, final Integer port) {
        return new FakeNode(host, _threadPool);
    }
}