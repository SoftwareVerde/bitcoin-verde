package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface PendingBlockStore extends BlockStore {
    Boolean storePendingBlock(Block block);
    void removePendingBlock(Sha256Hash blockHash);
    ByteArray getPendingBlockData(Sha256Hash blockHash);
    Boolean pendingBlockExists(Sha256Hash blockHash);
}
