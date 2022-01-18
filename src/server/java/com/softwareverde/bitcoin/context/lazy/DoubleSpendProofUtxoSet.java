package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnconfirmedTransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class DoubleSpendProofUtxoSet extends LazyUnconfirmedTransactionUtxoSet {

    public DoubleSpendProofUtxoSet(final FullNodeDatabaseManager databaseManager) {
        super(databaseManager, true);
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
            final UnconfirmedTransactionOutputDatabaseManager unconfirmedTransactionOutputDatabaseManager = _databaseManager.getUnconfirmedTransactionOutputDatabaseManager();

            final TransactionOutput transactionOutput = unspentTransactionOutputDatabaseManager.loadUnspentTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) {
                return transactionOutput;
            }

            // Disregard whether or not the UTXO has been spent within the mempool...
            final UnconfirmedTransactionOutputId transactionOutputId = unconfirmedTransactionOutputDatabaseManager.getUnconfirmedTransactionOutputId(transactionOutputIdentifier);
            return unconfirmedTransactionOutputDatabaseManager.getUnconfirmedTransactionOutput(transactionOutputId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }
}
