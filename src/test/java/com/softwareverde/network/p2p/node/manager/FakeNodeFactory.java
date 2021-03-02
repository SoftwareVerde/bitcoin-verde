package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.network.socket.BinarySocket;

public class FakeNodeFactory extends BitcoinNodeFactory {
    protected final ThreadPool _threadPool;

    public FakeNodeFactory(final ThreadPool threadPool) {
        super(null, null, null);
        _threadPool = threadPool;
    }

    @Override
    public FakeNode newNode(final String host, final Integer port) {
        return new FakeNode(host, _threadPool);
    }

    @Override
    public BitcoinNode newNode(final BinarySocket binarySocket) {
        return new FakeNode(binarySocket.getHost(), _threadPool);
    }
}