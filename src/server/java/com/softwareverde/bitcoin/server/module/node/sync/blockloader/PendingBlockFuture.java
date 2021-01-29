package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

import java.util.concurrent.ConcurrentLinkedDeque;

public class PendingBlockFuture implements PreloadedPendingBlock {
    protected final BlockInflater _blockInflater;
    protected final Pin _pin;
    protected final Sha256Hash _blockHash;
    protected final ConcurrentLinkedDeque<PendingBlockFuture> _predecessorBlocks = new ConcurrentLinkedDeque<PendingBlockFuture>();

    protected volatile PendingBlock _pendingBlock;
    protected volatile Long _blockHeight;
    protected volatile MutableUnspentTransactionOutputSet _unspentTransactionOutputSet;
    protected volatile Boolean _wasUnspentTransactionOutputSetInvalidated = false;

    protected void setLoadedPendingBlock(final Long blockHeight, final PendingBlock pendingBlock, final MutableUnspentTransactionOutputSet unspentTransactionOutputSet) {
        _blockHeight = blockHeight;
        _pendingBlock = pendingBlock;
        _unspentTransactionOutputSet = unspentTransactionOutputSet;
        _pin.release();
    }

    public PendingBlockFuture(final Sha256Hash blockHash) {
        this(blockHash, null);
    }

    public PendingBlockFuture(final Sha256Hash blockHash, final BlockInflater blockInflater) {
        _pin = new Pin();
        _blockHash = blockHash;
        _blockInflater = blockInflater;

        _pendingBlock = null;
        _unspentTransactionOutputSet = null;
    }

    public Sha256Hash getBlockHash() {
        return _blockHash;
    }

    /**
     * Returns the PendingBlock once it has been loaded, or null if it has not been loaded yet.
     */
    @Override
    public PendingBlock getPendingBlock() {
        if (! _pin.wasReleased()) {
            Logger.debug("Attempted to get block on unreleased pending block.", new Exception());
            return null;
        }

        return _pendingBlock;
    }

    /**
     * Returns the TransactionOutputSet for the PendingBlock once it has been loaded, or null if it has not been loaded yet.
     */
    @Override
    public MutableUnspentTransactionOutputSet getUnspentTransactionOutputSet() {
        if (! _pin.wasReleased()) { return null; }
        if (_unspentTransactionOutputSet == null) { return null; } // _unspentTransactionOutputSet may be set to null for blocks skipping validation...
        if (_wasUnspentTransactionOutputSetInvalidated) { return null; } // Force live-loading of UTXO sets if the set was explicitly invalidated (i.e. blockchain reorgs).

        // Update the UnspentTransactionOutputSet with the previously cached blocks...
        //  The previous Blocks that weren't processed at the time of the initial load are loaded into the UnspentTransactionOutputSet.
        while (! _predecessorBlocks.isEmpty()) {
            final PendingBlockFuture pendingBlockFuture = _predecessorBlocks.removeFirst();
            // Logger.trace(_blockHash + " outputs are being updated with " + pendingBlockFuture.getBlockHash() + " outputs.");
            try {
                final Boolean wasAvailable = pendingBlockFuture.waitFor(500L); // TODO: Handle this scenario better and determine why this timeout can happen...
                if (! wasAvailable) {
                    return null;
                }
            }
            catch (final Exception exception) {
                return null;
            }

            final PendingBlock pendingBlock = pendingBlockFuture.getPendingBlock();
            if (pendingBlock == null) {
                Logger.debug(_blockHash + " was unable to get a predecessor block.");
                return null;
            }

            final Block previousBlock;
            {
                final Block inflatedBlock = pendingBlock.getInflatedBlock();
                if (inflatedBlock != null) {
                    previousBlock = inflatedBlock;
                }
                else {
                    if (_blockInflater == null) {
                        Logger.debug("No BlockInflater found. " + _blockHash + " was unable to get a predecessor block.");
                        return null;
                    }
                    previousBlock = pendingBlock.inflateBlock(_blockInflater);
                }
            }

            // Logger.trace("Updating " + _blockHash + " with outputs from " + previousBlock.getHash());
            final Long blockHeight = pendingBlockFuture.getBlockHeight();
            _unspentTransactionOutputSet.update(previousBlock, blockHeight);
        }

        return _unspentTransactionOutputSet;
    }

    public void addPredecessorBlock(final PendingBlockFuture pendingBlockFuture) {
        _predecessorBlocks.addLast(pendingBlockFuture);
    }

    public Long getBlockHeight() {
        if (! _pin.wasReleased()) { return null; }

        return _blockHeight;
    }

    public void invalidateUnspentTransactionOutputSet() {
        _wasUnspentTransactionOutputSetInvalidated = true;
    }

    /**
     * Blocks until the PendingBlock and its TransactionOutputSet have been loaded.
     */
    public void waitFor() {
        _pin.waitForRelease();
    }

    /**
     * Blocks until the PendingBlock and its TransactionOutputSet have been loaded.
     *  Returns false if the timeout was exceeded while waiting.
     */
    public Boolean waitFor(final Long timeout) throws InterruptedException {
        return _pin.waitForRelease(timeout);
    }
}
