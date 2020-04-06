package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class FakeBlockStore implements PendingBlockStore {

    public FakeBlockStore(final String blockDataDirectory, final String pendingBlockDataDirectory, final BlockInflaters blockInflaters) {
    }

    @Override
    public Boolean storePendingBlock(final Block block) {
        return null;
    }

    @Override
    public void removePendingBlock(final Sha256Hash blockHash) {

    }

    @Override
    public ByteArray getPendingBlockData(final Sha256Hash blockHash) {
        return null;
    }

    @Override
    public Boolean pendingBlockExists(final Sha256Hash blockHash) {
        return null;
    }

    @Override
    public Boolean storeBlock(final Block block, final Long blockHeight) {
        return null;
    }

    @Override
    public void removeBlock(final Sha256Hash blockHash, final Long blockHeight) {

    }

    @Override
    public Block getBlock(final Sha256Hash blockHash, final Long blockHeight) {
        return null;
    }

    @Override
    public ByteArray readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        return null;
    }
}
