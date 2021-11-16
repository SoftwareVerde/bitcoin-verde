package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

import java.util.concurrent.ConcurrentHashMap;

public class DoubleSpendProofUtxoSet extends LazyUnconfirmedTransactionUtxoSet {
    protected final ConcurrentHashMap<TransactionOutputIdentifier, TransactionOutput> _alwaysAvailableOutputs = new ConcurrentHashMap<>();

    public DoubleSpendProofUtxoSet(final FullNodeDatabaseManager databaseManager) {
        super(databaseManager);
    }

    public DoubleSpendProofUtxoSet(final FullNodeDatabaseManager databaseManager, final Boolean includeUnconfirmedTransactions) {
        super(databaseManager, includeUnconfirmedTransactions);
    }

    public void addTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput) {
        _alwaysAvailableOutputs.put(transactionOutputIdentifier, transactionOutput);
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final TransactionOutput alwaysAvailableOutput = _alwaysAvailableOutputs.get(transactionOutputIdentifier);
        if (alwaysAvailableOutput != null) {
            return alwaysAvailableOutput;
        }

        return super.getTransactionOutput(transactionOutputIdentifier);
    }
}
