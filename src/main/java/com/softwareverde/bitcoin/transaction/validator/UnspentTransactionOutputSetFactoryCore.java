package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class UnspentTransactionOutputSetFactoryCore implements UnspentTransactionOutputSetFactory {

    protected final HashMap<TransactionOutputIdentifier, TransactionOutput> _blockTransactionOutputs;

    public UnspentTransactionOutputSetFactoryCore(final Block block) {
        final List<Transaction> transactions = block.getTransactions();
        final HashMap<TransactionOutputIdentifier, TransactionOutput> blockTransactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>();
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            for (int i = 0; i < transactionOutputs.getCount(); ++i) {
                final TransactionOutput transactionOutput = transactionOutputs.get(i);
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, i);
                blockTransactionOutputs.put(transactionOutputIdentifier, transactionOutput);
            }
        }
        _blockTransactionOutputs = blockTransactionOutputs;
    }

    @Override
    public UnspentTransactionOutputSet newUnspentTransactionOutputSet(final FullNodeDatabaseManager databaseManager) {
        return new UnspentTransactionOutputSetCore(databaseManager, _blockTransactionOutputs);
    }
}
