package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

import java.util.HashMap;

public class FakeUnspentTransactionOutputContext implements UnspentTransactionOutputContext {
    protected final Boolean _logNonExistentOutputs;
    protected final HashMap<TransactionOutputIdentifier, TransactionOutput> _transactionOutputs = new HashMap<>();
    protected final HashMap<TransactionOutputIdentifier, Boolean> _transactionCoinbaseStatuses = new HashMap<>();
    protected final HashMap<TransactionOutputIdentifier, Sha256Hash> _transactionBlockHashes = new HashMap<>();
    protected final HashMap<TransactionOutputIdentifier, Long> _transactionBlockHeights = new HashMap<>();
    protected final HashMap<TransactionOutputIdentifier, Boolean> _patfoStatuses = new HashMap<>();

    public FakeUnspentTransactionOutputContext() {
        _logNonExistentOutputs = true;
    }

    public FakeUnspentTransactionOutputContext(final Boolean logNonExistentOutputs) {
        _logNonExistentOutputs = logNonExistentOutputs;
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        if ( (! _transactionOutputs.containsKey(transactionOutputIdentifier)) && _logNonExistentOutputs ) {
            Logger.debug("Requested non-existent output: " + transactionOutputIdentifier, new Exception());
        }
        return _transactionOutputs.get(transactionOutputIdentifier);
    }

    @Override
    public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
        if ((! _transactionBlockHeights.containsKey(transactionOutputIdentifier)) && _logNonExistentOutputs ) {
            Logger.debug("Requested non-existent output blockHeight: " + transactionOutputIdentifier, new Exception());
        }

        return _transactionBlockHeights.get(transactionOutputIdentifier);
    }

    @Override
    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        if ( (! _transactionBlockHeights.containsKey(transactionOutputIdentifier)) && _logNonExistentOutputs ) {
            Logger.debug("Requested non-existent coinbase output: " + transactionOutputIdentifier, new Exception());
        }

        return _transactionCoinbaseStatuses.get(transactionOutputIdentifier);
    }

    @Override
    public Boolean isPreActivationTokenForgery(final TransactionOutputIdentifier transactionOutputIdentifier) {
        if ( (! _patfoStatuses.containsKey(transactionOutputIdentifier)) && _logNonExistentOutputs ) {
            Logger.debug("Requested non-existent PATFO status: " + transactionOutputIdentifier, new Exception());
        }

        return _patfoStatuses.get(transactionOutputIdentifier);
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
            _patfoStatuses.put(transactionOutputIdentifier, false);
            outputIndex += 1;
        }
    }

    public void setPatfoStatus(final TransactionOutputIdentifier transactionOutputIdentifier, final Boolean isPatfo) {
        _patfoStatuses.put(transactionOutputIdentifier, isPatfo);
    }

    public void clear() {
        _transactionOutputs.clear();
    }
}
