package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnconfirmedTransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class LazyUnconfirmedTransactionUtxoSet implements UnspentTransactionOutputContext {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final FullNodeDatabaseManager _databaseManager;
    protected final Boolean _includeUnconfirmedTransactions;

    public LazyUnconfirmedTransactionUtxoSet(final FullNodeDatabaseManager databaseManager, final UpgradeSchedule upgradeSchedule) {
        this(databaseManager, upgradeSchedule, false);
    }

    public LazyUnconfirmedTransactionUtxoSet(final FullNodeDatabaseManager databaseManager, final UpgradeSchedule upgradeSchedule, final Boolean includeUnconfirmedTransactions) {
        _databaseManager = databaseManager;
        _includeUnconfirmedTransactions = includeUnconfirmedTransactions;
        _upgradeSchedule = upgradeSchedule;
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            if (_includeUnconfirmedTransactions) {
                final UnconfirmedTransactionOutputDatabaseManager unconfirmedTransactionOutputDatabaseManager = _databaseManager.getUnconfirmedTransactionOutputDatabaseManager();
                final Boolean transactionOutputIsSpentWithinMempool = unconfirmedTransactionOutputDatabaseManager.isTransactionOutputSpent(transactionOutputIdentifier);
                if (transactionOutputIsSpentWithinMempool) { return null; }
            }

            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
            final TransactionOutput transactionOutput = unspentTransactionOutputDatabaseManager.loadUnspentTransactionOutput(transactionOutputIdentifier);
            if (! _includeUnconfirmedTransactions) {
                return transactionOutput;
            }
            else if (transactionOutput == null) {
                final UnconfirmedTransactionOutputDatabaseManager unconfirmedTransactionOutputDatabaseManager = _databaseManager.getUnconfirmedTransactionOutputDatabaseManager();
                final UnconfirmedTransactionOutputId transactionOutputId = unconfirmedTransactionOutputDatabaseManager.getUnconfirmedTransactionOutputId(transactionOutputIdentifier);
                return unconfirmedTransactionOutputDatabaseManager.getUnconfirmedTransactionOutput(transactionOutputId);
            }
            else {
                return transactionOutput;
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();

            final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) { return null; }

            // final Boolean isUnconfirmedTransaction = transactionDatabaseManager.isUnconfirmedTransaction(transactionId);
            // if (isUnconfirmedTransaction) {
            //     final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            //     return (blockHeaderDatabaseManager.getBlockHeight(headBlockId) + 1L);
            // }

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getBlockHeight(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) { return null; }

            return transactionDatabaseManager.isCoinbaseTransaction(transactionHash);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Boolean isPreActivationTokenForgery(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();

            final TransactionOutput transactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) {
                if (! transactionOutput.hasCashToken()) { return false; }

                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                if (transactionId == null) { return null; }

                final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
                final MedianBlockTime medianTimePast = blockHeaderDatabaseManager.getMedianTimePast(blockId);
                return (! _upgradeSchedule.areCashTokensEnabled(medianTimePast));
            }
            else { // Unconfirmed UTXO.
                final UnconfirmedTransactionOutputDatabaseManager unconfirmedTransactionOutputDatabaseManager = _databaseManager.getUnconfirmedTransactionOutputDatabaseManager();
                final UnconfirmedTransactionOutputId transactionOutputId = unconfirmedTransactionOutputDatabaseManager.getUnconfirmedTransactionOutputId(transactionOutputIdentifier);
                final TransactionOutput unconfirmedTransactionOutput = unconfirmedTransactionOutputDatabaseManager.getUnconfirmedTransactionOutput(transactionOutputId);
                if (unconfirmedTransactionOutput == null) { return null; }
                if (! unconfirmedTransactionOutput.hasCashToken()) { return false; }

                final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.getMedianBlockTime(headBlockId);
                return (! _upgradeSchedule.areCashTokensEnabled(medianBlockTime));
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }
}
