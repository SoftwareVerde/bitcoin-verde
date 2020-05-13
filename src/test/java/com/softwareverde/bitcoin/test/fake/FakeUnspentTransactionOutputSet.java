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
    protected final HashMap<TransactionOutputIdentifier, Boolean> _transactionCoinbaseStatuses = new HashMap<TransactionOutputIdentifier, Boolean>();
    protected final HashMap<TransactionOutputIdentifier, Sha256Hash> _transactionBlockHashes = new HashMap<TransactionOutputIdentifier, Sha256Hash>();
    protected final HashMap<TransactionOutputIdentifier, Long> _transactionBlockHeights = new HashMap<TransactionOutputIdentifier, Long>();

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionOutputs.get(transactionOutputIdentifier);
    }

    @Override
    public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionBlockHeights.get(transactionOutputIdentifier);
    }

    @Override
    public Sha256Hash getBlockHash(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionBlockHashes.get(transactionOutputIdentifier);
    }

    @Override
    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionCoinbaseStatuses.get(transactionOutputIdentifier);
    }

    public void addTransaction(final Transaction transaction, final Sha256Hash blockHash, final Long blockHeight, final Boolean isCoinbaseTransaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final Sha256Hash transactionHash = transaction.getHash();
        int outputIndex = 0;
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
            _transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
            _transactionBlockHeights.put(transactionOutputIdentifier, blockHeight);
            _transactionCoinbaseStatuses.put(transactionOutputIdentifier, isCoinbaseTransaction);
            _transactionBlockHashes.put(transactionOutputIdentifier, blockHash);
            outputIndex += 1;
        }
    }

    public void clear() {
        _transactionOutputs.clear();
    }
}
