package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;

public interface PreloadedPendingBlock {
    PendingBlock getPendingBlock();
    MutableUnspentTransactionOutputSet getUnspentTransactionOutputSet();
}
