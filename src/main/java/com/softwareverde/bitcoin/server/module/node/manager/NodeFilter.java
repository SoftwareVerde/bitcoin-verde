package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.node.BitcoinNode;

public interface NodeFilter {
    Boolean meetsCriteria(BitcoinNode bitcoinNode);
}
