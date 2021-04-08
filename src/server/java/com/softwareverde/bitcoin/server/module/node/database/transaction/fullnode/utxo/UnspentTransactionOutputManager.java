package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.BlockLoader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PreloadedBlock;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

public class UnspentTransactionOutputManager {
    public static void lockUtxoSet() {
        UnspentTransactionOutputDatabaseManager.lockUtxoSet();
    }

    public static void unlockUtxoSet() {
        UnspentTransactionOutputDatabaseManager.unlockUtxoSet();
    }

    public static void invalidateUncommittedUtxoSet() {
        UnspentTransactionOutputDatabaseManager.invalidateUncommittedUtxoSet();
    }

    public static Boolean isUtxoCacheReady() {
        return UnspentTransactionOutputDatabaseManager.isUtxoCacheReady();
    }

    protected final FullNodeDatabaseManager _databaseManager;
    protected final Long _commitFrequency;

    protected void _buildUtxoSetUpToHeadBlock(final BlockLoader blockLoader, final DatabaseManagerFactory databaseManagerFactory) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = _databaseManager.getBlockDatabaseManager();
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();

        final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
        final long maxBlockHeight = Util.coalesce(blockHeaderDatabaseManager.getBlockHeight(headBlockId), 0L);

        long blockHeight = (unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight() + 1L); // inclusive
        if (blockHeight <= maxBlockHeight) {
            Logger.info("UTXO set is " + ((maxBlockHeight - blockHeight) + 1) + " blocks behind.");
        }
        while (blockHeight <= maxBlockHeight) {
            final PreloadedBlock preloadedBlock = blockLoader.getBlock(blockHeight);
            final Block block = (preloadedBlock != null ? preloadedBlock.getBlock() : null);
            if ( (preloadedBlock == null) || (block == null) ) {
                Logger.debug("Unable to load block: " + blockHeight);
                break;
            }

            Logger.trace("Applying block " + blockHeight + " to UTXO set.");
            _updateUtxoSetWithBlock(block, blockHeight, databaseManagerFactory);
            blockHeight += 1L;
        }

        final Long utxoBlockHeight = (blockHeight - 1L);
        unspentTransactionOutputDatabaseManager.setUncommittedUnspentTransactionOutputBlockHeight(utxoBlockHeight);
    }

    protected void _commitInMemoryUtxoSetToDisk(final DatabaseManagerFactory databaseManagerFactory) throws DatabaseException {
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
        Logger.info("Committing UTXO set.");
        unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(databaseManagerFactory, CommitAsyncMode.BLOCK_IF_BUSY);
    }

    protected void _updateUtxoSetWithBlock(final Block block, final Long blockHeight, final DatabaseManagerFactory databaseManagerFactory) throws DatabaseException {
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final MilliTimer totalTimer = new MilliTimer();
        final MilliTimer utxoCommitTimer = new MilliTimer();
        final MilliTimer utxoTimer = new MilliTimer();

        totalTimer.start();

        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();

        int unspendableCount = 0;
        final MutableList<TransactionOutputIdentifier> spentTransactionOutputIdentifiers = new MutableList<TransactionOutputIdentifier>();
        final MutableList<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers = new MutableList<TransactionOutputIdentifier>();
        for (int i = 0; i < transactions.getCount(); ++i) {
            final Transaction transaction = transactions.get(i);
            final Sha256Hash transactionHash = transaction.getHash();
            final Sha256Hash constTransactionHash = transactionHash.asConst();

            final boolean isCoinbase = (i == 0);
            if (! isCoinbase) {
                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    spentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                }
            }

            int outputIndex = 0;
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final boolean isPossiblySpendable = (! scriptPatternMatcher.isProvablyUnspendable(lockingScript));

                if (isPossiblySpendable) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(constTransactionHash, outputIndex);
                    unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                }
                else {
                    unspendableCount += 1;
                }

                outputIndex += 1;
            }
        }

        final int worstCaseNewUtxoCount = (unspentTransactionOutputIdentifiers.getCount() + spentTransactionOutputIdentifiers.getCount());
        final Long uncommittedUtxoCount = unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputCount();
        if ( ((blockHeight % _commitFrequency) == 0L) || ( (uncommittedUtxoCount + worstCaseNewUtxoCount) >= unspentTransactionOutputDatabaseManager.getMaxUtxoCount()) ) {
            Logger.trace("((" + blockHeight + " % " + _commitFrequency + ") == 0) || ((" + uncommittedUtxoCount + " + " + worstCaseNewUtxoCount + ") >= " + unspentTransactionOutputDatabaseManager.getMaxUtxoCount() + ")");
            utxoCommitTimer.start();
            _commitInMemoryUtxoSetToDisk(databaseManagerFactory);
            utxoCommitTimer.stop();
            Logger.debug("Commit Timer: " + utxoCommitTimer.getMillisecondsElapsed() + "ms.");
        }

        utxoTimer.start();

        unspentTransactionOutputDatabaseManager.insertUnspentTransactionOutputs(unspentTransactionOutputIdentifiers, blockHeight);
        unspentTransactionOutputDatabaseManager.markTransactionOutputsAsSpent(spentTransactionOutputIdentifiers);

        utxoTimer.stop();
        totalTimer.stop();

        unspentTransactionOutputDatabaseManager.setUncommittedUnspentTransactionOutputBlockHeight(blockHeight);
        Logger.trace("UTXO Block Height: " + unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputBlockHeight());
        Logger.debug("BlockHeight: " + blockHeight + " " + unspentTransactionOutputIdentifiers.getCount() + " unspent, " + spentTransactionOutputIdentifiers.getCount() + " spent, " + unspendableCount + " unspendable. " + transactionCount + " transactions in " + totalTimer.getMillisecondsElapsed() + " ms (" + (transactionCount * 1000L / (totalTimer.getMillisecondsElapsed() + 1L)) + " tps), " + utxoTimer.getMillisecondsElapsed() + "ms for UTXOs. " + (transactions.getCount() * 1000L / (utxoTimer.getMillisecondsElapsed() + 1L)) + " tps.");
    }

    public UnspentTransactionOutputManager(final FullNodeDatabaseManager databaseManager, final Long commitFrequency) {
        _databaseManager = databaseManager;
        _commitFrequency = commitFrequency;
    }

    /**
     * Destroys the In-Memory and On-Disk UTXO set and rebuilds it from the Genesis Block.
     */
    public void rebuildUtxoSetFromGenesisBlock(final BlockLoader blockLoader, final DatabaseManagerFactory databaseManagerFactory) throws DatabaseException {
        UnspentTransactionOutputDatabaseManager.lockUtxoSet();
        try {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();

            unspentTransactionOutputDatabaseManager.clearUncommittedUtxoSet();
            unspentTransactionOutputDatabaseManager.clearCommittedUtxoSet();
            _buildUtxoSetUpToHeadBlock(blockLoader, databaseManagerFactory);
        }
        catch (final Exception exception) {
            UnspentTransactionOutputDatabaseManager.invalidateUncommittedUtxoSet();
            throw exception;
        }
        finally {
            UnspentTransactionOutputDatabaseManager.unlockUtxoSet();
        }
    }

    /**
     * Builds the UTXO set from the last committed block.
     */
    public void buildUtxoSet(final BlockLoader blockLoader, final DatabaseManagerFactory databaseManagerFactory) throws DatabaseException {
        UnspentTransactionOutputDatabaseManager.lockUtxoSet();
        try {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();

            unspentTransactionOutputDatabaseManager.clearUncommittedUtxoSet();
            _buildUtxoSetUpToHeadBlock(blockLoader, databaseManagerFactory);
        }
        catch (final Exception exception) {
            UnspentTransactionOutputDatabaseManager.invalidateUncommittedUtxoSet();
            throw exception;
        }
        finally {
            UnspentTransactionOutputDatabaseManager.unlockUtxoSet();
        }
    }

    /**
     * Updates the in-memory UTXO set for the provided Block.
     *  This function may do a disk-commit of the in-memory UTXO set.
     *  A disk-commit is executed periodically based on block height and/or if the in-memory set grows too large.
     */
    public void applyBlockToUtxoSet(final Block block, final Long blockHeight, final DatabaseManagerFactory databaseManagerFactory) throws DatabaseException {
        UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.lock();
        try {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
            final Long uncommittedUtxoBlockHeight = unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputBlockHeight();
            if (! Util.areEqual(blockHeight, (uncommittedUtxoBlockHeight + 1L))) {
                throw new DatabaseException("Attempted to update UTXO set with out-of-order block. blockHeight=" + blockHeight + ", utxoHeight=" + uncommittedUtxoBlockHeight);
            }

            _updateUtxoSetWithBlock(block, blockHeight, databaseManagerFactory);
        }
        catch (final Exception exception) {
            UnspentTransactionOutputDatabaseManager.invalidateUncommittedUtxoSet();
            throw exception;
        }
        finally {
            UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.unlock();
        }
    }

    /**
     * Removes UTXOs generated, and re-adds UTXOs spent, by the provided Block.
     */
    public void removeBlockFromUtxoSet(final Block block, final Long blockHeight) throws DatabaseException {
        Logger.debug("Un-Applying Block from UTXO set: " + block.getHash());

        UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.lock();
        try {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
            final Long uncommittedUtxoBlockHeight = unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputBlockHeight();
            if (! Util.areEqual(blockHeight, uncommittedUtxoBlockHeight)) {
                throw new DatabaseException("Attempted to update UTXO set with out-of-order block. blockHeight=" + blockHeight + ", utxoHeight=" + uncommittedUtxoBlockHeight);
            }

            final List<Transaction> transactions = block.getTransactions();
            final MutableList<TransactionOutputIdentifier> previousOutputIdentifiers = new MutableList<>();
            final MutableList<TransactionOutputIdentifier> newOutputIdentifiers = new MutableList<>();
            for (int i = 0; i < transactions.getCount(); ++i) {
                final Transaction transaction = transactions.get(i);

                final boolean isCoinbase = (i == 0);
                if (! isCoinbase) {
                    for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                        final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                        previousOutputIdentifiers.add(transactionOutputIdentifier);
                    }
                }

                final List<TransactionOutputIdentifier> transactionOutputIdentifiers = TransactionOutputIdentifier.fromTransactionOutputs(transaction);
                newOutputIdentifiers.addAll(transactionOutputIdentifiers);
            }

            unspentTransactionOutputDatabaseManager.undoCreationOfTransactionOutputs(newOutputIdentifiers);
            unspentTransactionOutputDatabaseManager.undoSpendingOfTransactionOutputs(previousOutputIdentifiers);
            unspentTransactionOutputDatabaseManager.setUncommittedUnspentTransactionOutputBlockHeight(blockHeight - 1L);
            Logger.trace("UTXO Block Height: " + (blockHeight - 1L) + " " + unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputBlockHeight());
        }
        catch (final Exception exception) {
            UnspentTransactionOutputDatabaseManager.invalidateUncommittedUtxoSet();
            throw exception;
        }
        finally {
            UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.unlock();
        }
    }

    public void clearUncommittedUtxoSet() throws DatabaseException {
        UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.lock();
        try {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
            unspentTransactionOutputDatabaseManager.clearUncommittedUtxoSet();
        }
        finally {
            UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.unlock();
        }
    }

    public Long getCommitFrequency() {
        return _commitFrequency;
    }
}
