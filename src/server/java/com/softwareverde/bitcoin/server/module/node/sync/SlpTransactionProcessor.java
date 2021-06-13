package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidationCache;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidator;
import com.softwareverde.bitcoin.slp.validator.TransactionAccumulator;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Map;

public class SlpTransactionProcessor extends SleepyService {
    public static final Integer BATCH_SIZE = 4096;

    public static TransactionAccumulator createTransactionAccumulator(final FullNodeDatabaseManager databaseManager, final Container<Integer> nullableTransactionLookupCount) {
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

        return new TransactionAccumulator() {
                @Override
                public Map<Sha256Hash, Transaction> getTransactions(final List<Sha256Hash> transactionHashes, final Boolean allowUnconfirmedTransactions) {
                    try {
                        final HashMap<Sha256Hash, Transaction> transactions = new HashMap<Sha256Hash, Transaction>(transactionHashes.getCount());
                        final MilliTimer milliTimer = new MilliTimer();
                        milliTimer.start();
                        for (final Sha256Hash transactionHash : transactionHashes) {
                            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                            if (transactionId == null) { continue; }

                            if (! allowUnconfirmedTransactions) {
                                final Boolean isUnconfirmedTransaction = transactionDatabaseManager.isUnconfirmedTransaction(transactionId);
                                if (isUnconfirmedTransaction) {
                                    continue;
                                }
                            }

                            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                            transactions.put(transactionHash, transaction);
                        }
                        milliTimer.stop();
                        if (nullableTransactionLookupCount != null) {
                            nullableTransactionLookupCount.value += transactionHashes.getCount();
                        }
                        Logger.trace("Loaded " + transactionHashes.getCount() + " in " + milliTimer.getMillisecondsElapsed() + "ms.");
                        return transactions;
                    }
                    catch (final DatabaseException exception) {
                        Logger.warn(exception);
                        return null;
                    }
                }
            };
    }

    public static SlpTransactionValidationCache createSlpTransactionValidationCache(final FullNodeDatabaseManager databaseManager) {
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

        return new SlpTransactionValidationCache() {
            @Override
            public Boolean isValid(final Sha256Hash transactionHash) {
                try {
                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    if (transactionId == null) { return null; }

                    final MilliTimer milliTimer = new MilliTimer();
                    milliTimer.start();
                    final Boolean result = slpTransactionDatabaseManager.getSlpTransactionValidationResult(transactionId);
                    milliTimer.stop();
                    Logger.trace("Loaded Cached Validity: " + transactionHash + " in " + milliTimer.getMillisecondsElapsed() + "ms. (" + result + ")");
                    return result;
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                    return null;
                }
            }

            @Override
            public void setIsValid(final Sha256Hash transactionHash, final Boolean isValid) {
                try {
                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    if (transactionId == null) { return; }

                    slpTransactionDatabaseManager.setSlpTransactionValidationResult(transactionId, isValid);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                }
            }
        };
    }

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    @Override
    protected void _onStart() {
        Logger.trace("SlpTransactionProcessor Starting.");
    }

    @Override
    protected Boolean _run() {
        Logger.trace("SlpTransactionProcessor Running.");
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

            final Container<Integer> transactionLookupCount = new Container<Integer>(0);

            final TransactionAccumulator transactionAccumulator = SlpTransactionProcessor.createTransactionAccumulator(databaseManager, transactionLookupCount);
            final SlpTransactionValidationCache slpTransactionValidationCache = SlpTransactionProcessor.createSlpTransactionValidationCache(databaseManager);

            // 1. Iterate through blocks for SLP transactions.
            // 2. Validate any SLP transactions for that block's blockchain segment.
            // 3. Update those SLP transactions validation statuses via the slpTransactionDatabaseManager.
            // 4. Move on to the next block.

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            if (headBlockchainSegmentId == null) { return false; }

            BlockId nextBlockId;
            {
                BlockId lastMainChainBlockId = slpTransactionDatabaseManager.getLastSlpValidatedBlockId();
                if (lastMainChainBlockId.longValue() == 0L) {
                    lastMainChainBlockId = blockHeaderDatabaseManager.getBlockHeaderId(Sha256Hash.fromHexString(BitcoinConstants.getGenesisBlockHash()));
                }
                while (true) {
                    final Boolean blockIsOnMainChain = blockHeaderDatabaseManager.isBlockConnectedToChain(lastMainChainBlockId, headBlockchainSegmentId, BlockRelationship.ANY);
                    if (blockIsOnMainChain) { break; }

                    lastMainChainBlockId = blockHeaderDatabaseManager.getAncestorBlockId(lastMainChainBlockId, 1);
                }

                BlockId childBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, lastMainChainBlockId);
                while (childBlockId != null) {
                    final Boolean nextBlockHasTransactions = blockDatabaseManager.hasTransactions(childBlockId);
                    if (nextBlockHasTransactions) { break; }
                    childBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, childBlockId);
                }
                nextBlockId = childBlockId;
            }

            final SlpTransactionValidator slpTransactionValidator = new SlpTransactionValidator(transactionAccumulator, slpTransactionValidationCache);
            if (nextBlockId != null) {
                // Validate Confirmed SLP Transactions...
                final List<TransactionId> pendingSlpTransactionIds = slpTransactionDatabaseManager.getConfirmedPendingValidationSlpTransactions(nextBlockId);

                final MilliTimer milliTimer = new MilliTimer();

                for (final TransactionId transactionId : pendingSlpTransactionIds) {
                    transactionLookupCount.value = 0;
                    milliTimer.start();

                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    final Boolean isValid = slpTransactionValidator.validateTransaction(transaction);
                    slpTransactionDatabaseManager.setSlpTransactionValidationResult(transactionId, isValid);

                    milliTimer.stop();
                    Logger.trace("Validated Slp Tx " + transaction.getHash() + " in " + milliTimer.getMillisecondsElapsed() + "ms. IsValid: " + isValid + " (lookUps=" + transactionLookupCount.value + ")");
                }
                slpTransactionDatabaseManager.setLastSlpValidatedBlockId(nextBlockId);
            }
            else { // Only validate unconfirmed SLP Transactions if the history is up to date in order to reduce the validation depth.
                // Validate Unconfirmed SLP Transactions...
                final List<TransactionId> unconfirmedPendingSlpTransactionIds = slpTransactionDatabaseManager.getUnconfirmedPendingValidationSlpTransactions(BATCH_SIZE);
                if (unconfirmedPendingSlpTransactionIds.isEmpty()) { return false; }

                final MilliTimer milliTimer = new MilliTimer();
                for (final TransactionId transactionId : unconfirmedPendingSlpTransactionIds) {
                    transactionLookupCount.value = 0;
                    milliTimer.start();

                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    final Boolean isValid = slpTransactionValidator.validateTransaction(transaction);
                    slpTransactionDatabaseManager.setSlpTransactionValidationResult(transactionId, isValid);

                    milliTimer.stop();
                    Logger.trace("Validated Unconfirmed Slp Tx " + transaction.getHash() + " in " + milliTimer.getMillisecondsElapsed() + "ms. IsValid: " + isValid + " (lookUps=" + transactionLookupCount.value + ")");
                }
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return false;
        }

        Logger.trace("SlpTransactionProcessor Stopping.");
        return true;
    }

    @Override
    protected void _onSleep() {
        Logger.trace("SlpTransactionProcessor Sleeping.");
    }

    public SlpTransactionProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }
}
