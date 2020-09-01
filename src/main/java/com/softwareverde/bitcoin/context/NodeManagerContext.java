package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;

public interface NodeManagerContext {
    BitcoinNodeManager getNodeManager();
}
