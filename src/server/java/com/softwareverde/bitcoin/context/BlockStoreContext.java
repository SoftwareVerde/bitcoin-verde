package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.module.node.store.BlockStore;

public interface BlockStoreContext {
    BlockStore getBlockStore();
}
