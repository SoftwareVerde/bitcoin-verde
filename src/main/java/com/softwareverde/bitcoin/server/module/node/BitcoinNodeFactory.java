package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
import com.softwareverde.network.p2p.node.NodeFactory;

public class BitcoinNodeFactory implements NodeFactory<BitcoinNode> {
    protected final ThreadPoolFactory _threadPoolFactory;

    public BitcoinNodeFactory(final ThreadPoolFactory threadPoolFactory) {
        _threadPoolFactory = threadPoolFactory;
    }

    @Override
    public BitcoinNode newNode(final String host, final Integer port) {
        return new BitcoinNode(host, port, _threadPoolFactory.newThreadPool());
    }
}
