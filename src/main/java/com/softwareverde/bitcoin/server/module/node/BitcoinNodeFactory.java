package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.network.p2p.node.NodeFactory;

public class BitcoinNodeFactory implements NodeFactory<BitcoinNode> {
    @Override
    public BitcoinNode newNode(final String host, final Integer port) {
        return new BitcoinNode(host, port);
    }
}
