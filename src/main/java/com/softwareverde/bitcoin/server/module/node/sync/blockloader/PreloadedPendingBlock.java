package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.context.MutableUnspentTransactionOutputSet;

public interface PreloadedPendingBlock {
    PendingBlock getPendingBlock();
    MutableUnspentTransactionOutputSet getUnspentTransactionOutputSet();
}
