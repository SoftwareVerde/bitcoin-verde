package com.softwareverde.bitcoin.server.module.node.handler.transaction;

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

public class RequestSlpTransactionsHandler implements BitcoinNode.RequestSlpTransactionsHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected Boolean _getSlpStatus(final FullNodeDatabaseManager databaseManager, final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
        return slpTransactionDatabaseManager.getSlpTransactionValidationResult(transactionId);
    }

    public RequestSlpTransactionsHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactionHashes) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final MutableList<Sha256Hash> validatedTransactionHashes = new MutableList<Sha256Hash>(transactionHashes.getCount());
            for (final Sha256Hash transactionHash : transactionHashes) {
                final Boolean isValid = _getSlpStatus(databaseManager, transactionHash);
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
            return _getSlpStatus(databaseManager, transactionHash);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }
}
