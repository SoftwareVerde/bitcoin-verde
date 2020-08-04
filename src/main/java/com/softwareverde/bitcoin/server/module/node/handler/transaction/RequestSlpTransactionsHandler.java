package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class RequestSlpTransactionsHandler implements BitcoinNode.RequestSlpTransactionsCallback {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected Boolean _getSlpStatus(final FullNodeDatabaseManager databaseManager, final BlockchainSegmentId blockchainSegmentId, final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
        return slpTransactionDatabaseManager.getSlpTransactionValidationResult(blockchainSegmentId, transactionId);
    }

    public RequestSlpTransactionsHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public void run(final List<Sha256Hash> transactionHashes, final BitcoinNode bitcoinNode) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final MutableList<Sha256Hash> validatedTransactionHashes = new MutableList<Sha256Hash>(transactionHashes.getCount());
            for (final Sha256Hash transactionHash : transactionHashes) {
                final Boolean isValid = _getSlpStatus(databaseManager, blockchainSegmentId, transactionHash);
                if (isValid != null) {
                    // add hash as long as we have validated it (no need to re-send known transaction hashes)
                    validatedTransactionHashes.add(transactionHash);
                }
            }

            bitcoinNode.transmitTransactionHashes(validatedTransactionHashes);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    @Override
    public Boolean getSlpStatus(final Sha256Hash transactionHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            return _getSlpStatus(databaseManager, blockchainSegmentId, transactionHash);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }
}
