package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class MockBlockStore implements PendingBlockStore {
    protected final BlockInflaters _blockInflaters = new CoreInflater();

    protected final HashMap<Sha256Hash, Block> _pendingBlocks = new HashMap<Sha256Hash, Block>();
    protected final HashMap<Sha256Hash, Block> _blocks = new HashMap<Sha256Hash, Block>();

    public MockBlockStore() { }

    @Override
    public Boolean storePendingBlock(final Block block) {
        _pendingBlocks.put(block.getHash(), block);
        return true;
    }

    @Override
    public void removePendingBlock(final Sha256Hash blockHash) {
        _pendingBlocks.remove(blockHash);
    }

    @Override
    public ByteArray getPendingBlockData(final Sha256Hash blockHash) {
        final Block block = _pendingBlocks.get(blockHash);
        if (block == null) { return null; }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        return blockDeflater.toBytes(block);
    }

    @Override
    public Boolean pendingBlockExists(final Sha256Hash blockHash) {
        return _pendingBlocks.containsKey(blockHash);
    }

    @Override
    public Boolean storeBlock(final Block block, final Long blockHeight) {
        _blocks.put(block.getHash(), block);
        return true;
    }

    @Override
    public void removeBlock(final Sha256Hash blockHash, final Long blockHeight) {
        _blocks.remove(blockHash);
    }

    @Override
    public MutableBlockHeader getBlockHeader(final Sha256Hash blockHash, final Long blockHeight) {
        final Block block = _blocks.get(blockHash);
        return new MutableBlockHeader(block);
    }

    @Override
    public MutableBlock getBlock(final Sha256Hash blockHash, final Long blockHeight) {
        final Block block = _blocks.get(blockHash);
        if (block instanceof MutableBlock) {
            return (MutableBlock) block;
        }

        return new MutableBlock(block);
    }

    @Override
    public ByteArray readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        final Block block = _blocks.get(blockHash);
        if (block == null) { return null; }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        final ByteArray byteArray = blockDeflater.toBytes(block);
        return ByteArray.wrap(byteArray.getBytes(diskOffset.intValue(), byteCount));
    }

    public void clear() {
        _pendingBlocks.clear();
        _blocks.clear();
    }
}
