package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.UnspentTransactionOutputSet;
import com.softwareverde.constable.list.List;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class FakeUnspentTransactionOutputSet implements UnspentTransactionOutputSet {
    protected final HashMap<TransactionOutputIdentifier, TransactionOutput> _transactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>();

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionOutputs.get(transactionOutputIdentifier);
    }

    public void addTransaction(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final Sha256Hash transactionHash = transaction.getHash();
        int outputIndex = 0;
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
            _transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
            outputIndex += 1;
        }
    }

    public void addTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput) {
        _transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
    }

    public void clear() {
        _transactionOutputs.clear();
    }
}
