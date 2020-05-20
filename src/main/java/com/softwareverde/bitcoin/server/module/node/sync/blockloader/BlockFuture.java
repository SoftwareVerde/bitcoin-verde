package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.transaction.validator.MutableUnspentTransactionOutputSet;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.concurrent.ConcurrentLinkedDeque;

public class BlockFuture implements PreloadedBlock {
    protected final Pin _pin;
    protected final Long _blockHeight;
    protected final ConcurrentLinkedDeque<PendingBlockFuture> _predecessorBlocks = new ConcurrentLinkedDeque<PendingBlockFuture>();

    protected volatile Block _block;

    public BlockFuture(final Long blockHeight) {
        _pin = new Pin();
        _blockHeight = blockHeight;

        _block = null;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    /**
     * Returns the Block once it has been loaded, or null if it has not been loaded yet.
     */
    @Override
    public Block getBlock() {
        if (! _pin.wasReleased()) {
            Logger.debug("Attempted to get block on unreleased block.", new Exception());
            return null;
        }

        return _block;
    }

    /**
     * Blocks until the PendingBlock and its TransactionOutputSet have been loaded.
     */
    public void waitFor() {
        _pin.waitForRelease();
    }
}