package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;
import java.util.Map;

public class UnspentTransactionOutputSetCore implements UnspentTransactionOutputSet {
    protected final FullNodeDatabaseManager _databaseManager;
    protected final Map<TransactionOutputIdentifier, TransactionOutput> _blockTransactionOutputs;

    protected UnspentTransactionOutputSetCore(final FullNodeDatabaseManager databaseManager, final Map<TransactionOutputIdentifier, TransactionOutput> blockOutputs) {
        _databaseManager = databaseManager;
        _blockTransactionOutputs = blockOutputs;
    }

    public UnspentTransactionOutputSetCore(final FullNodeDatabaseManager databaseManager, final Block block) {
        _databaseManager = databaseManager;

        final List<Transaction> transactions = block.getTransactions();
        _blockTransactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>();
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            for (int i = 0; i < transactionOutputs.getCount(); ++i) {
                final TransactionOutput transactionOutput = transactionOutputs.get(i);
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, i);
                _blockTransactionOutputs.put(transactionOutputIdentifier, transactionOutput);
            }
        }
    }

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final TransactionOutput transactionOutput = _blockTransactionOutputs.get(transactionOutputIdentifier);
        if (transactionOutput != null) {
            return transactionOutput;
        }

        try {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
        }
        catch (final DatabaseException exception) {
            Logger.error(exception);
            return null;
        }
    }
}
