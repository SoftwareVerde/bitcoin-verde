package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.network.p2p.node.NodeFactory;

public class BitcoinNodeFactory implements NodeFactory<BitcoinNode> {
    protected final ThreadPool _threadPool;

    public BitcoinNodeFactory(final ThreadPool threadPool) {
        _threadPool = threadPool;
    }

    @Override
    public BitcoinNode newNode(final String host, final Integer port) {
        return new BitcoinNode(host, port, _threadPool);
    }
}
