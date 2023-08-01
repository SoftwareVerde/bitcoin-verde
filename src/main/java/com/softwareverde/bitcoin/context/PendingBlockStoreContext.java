package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;

public interface PendingBlockStoreContext {
    PendingBlockStore getPendingBlockStore();
}
