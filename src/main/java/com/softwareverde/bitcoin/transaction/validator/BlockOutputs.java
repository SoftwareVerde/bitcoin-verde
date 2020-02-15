package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;
import java.util.Map;

public class BlockOutputs {
    protected final Map<TransactionOutputIdentifier, TransactionOutput> _transactionOutputs;

    public static BlockOutputs fromBlock(final Block block) {
        final List<Transaction> transactions = block.getTransactions();
        final HashMap<TransactionOutputIdentifier, TransactionOutput> transactionOutputMap = new HashMap<TransactionOutputIdentifier, TransactionOutput>(transactions.getCount());

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            int outputIndex = 0;
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                transactionOutputMap.put(transactionOutputIdentifier, transactionOutput);
                outputIndex += 1;
            }
        }

        return new BlockOutputs(transactionOutputMap);
    }

    protected BlockOutputs(final Map<TransactionOutputIdentifier, TransactionOutput> transactionOutputs) {
        _transactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>(0);
    }

    public BlockOutputs() {
        _transactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>(0);
    }

    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return null;
    }
}
