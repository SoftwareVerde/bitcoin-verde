package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface FakeBlockStore extends PendingBlockStore {
    @Override
    default Boolean storeBlock(Block block, Long blockHeight) { throw new UnsupportedOperationException(); }

    @Override
    default void removeBlock(Sha256Hash blockHash, Long blockHeight) { throw new UnsupportedOperationException(); }

    @Override
    default MutableBlockHeader getBlockHeader(Sha256Hash blockHash, Long blockHeight) { throw new UnsupportedOperationException(); }

    @Override
    default MutableBlock getBlock(Sha256Hash blockHash, Long blockHeight) { throw new UnsupportedOperationException(); }

    @Override
    default ByteArray readFromBlock(Sha256Hash blockHash, Long blockHeight, Long diskOffset, Integer byteCount) { throw new UnsupportedOperationException(); }

    @Override
    default Boolean storePendingBlock(Block block) { throw new UnsupportedOperationException(); }

    @Override
    default void removePendingBlock(Sha256Hash blockHash) { throw new UnsupportedOperationException(); }

    @Override
    default ByteArray getPendingBlockData(Sha256Hash blockHash) { throw new UnsupportedOperationException(); }

    @Override
    default Boolean pendingBlockExists(Sha256Hash blockHash) { throw new UnsupportedOperationException(); }
}