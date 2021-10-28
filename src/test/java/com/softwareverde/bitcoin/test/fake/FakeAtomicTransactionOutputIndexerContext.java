package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.HashMap;
import java.util.Map;

public class FakeAtomicTransactionOutputIndexerContext implements com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext {
    protected final HashMap<Sha256Hash, TransactionId> _transactionIds = new HashMap<>(0);
    protected final HashMap<TransactionId, Transaction> _transactions = new HashMap<>(0);

    protected final MutableList<Address> _storedAddresses = new MutableList<>(0);
    protected final MutableList<TransactionId> _unprocessedTransactions = new MutableList<>(0);
    protected final MutableList<IndexedOutput> _indexedOutputs = new MutableList<>(0);
    protected final MutableList<IndexedInput> _indexedInputs = new MutableList<>(0);
    protected TransactionId _lastTransactionId = null;

    protected Boolean _wasCommitted = null;
    protected Boolean _wasRolledBack = false;
    protected Boolean _wasClosed = false;

    public void addTransaction(final Transaction transaction) {
        final Sha256Hash transactionHash = transaction.getHash();
        if (_transactionIds.containsKey(transactionHash)) { return; }

        final TransactionId transactionId = TransactionId.wrap(_transactions.size() + 1L);
        _transactionIds.put(transactionHash, transactionId);
        _transactions.put(transactionId, transaction);
    }

    public void queueTransactionForProcessing(final Transaction transaction) {
        final Sha256Hash transactionHash = transaction.getHash();
        final TransactionId transactionId;
        {
            if (_transactionIds.containsKey(transactionHash)) {
                transactionId = _transactionIds.get(transactionHash);
            }
            else {
                transactionId = TransactionId.wrap(_transactions.size() + 1L);
                _transactionIds.put(transactionHash, transactionId);
                _transactions.put(transactionId, transaction);
            }
        }

        _unprocessedTransactions.add(transactionId);
    }

    public Transaction getTransaction(final Sha256Hash transactionHash) {
        final TransactionId transactionId = _transactionIds.get(transactionHash);
        if (transactionId == null) { return null; }

        return _transactions.get(transactionId);
    }

    public List<TransactionId> getTransactionIds() {
        return new ImmutableList<>(_transactionIds.values());
    }

    public Boolean wasCommitted() {
        return _wasCommitted;
    }

    public Boolean wasRolledBack() {
        return _wasRolledBack;
    }

    public List<IndexedOutput> getIndexedOutputs() {
        return _indexedOutputs;
    }

    public List<Address> getStoredAddresses() {
        return _storedAddresses;
    }

    @Override
    public void initialize() { _wasCommitted = false; }

    @Override
    public TransactionId finish() {
        _wasCommitted = true;
        return _lastTransactionId;
    }

    @Override
    public void rollbackDatabaseTransaction() {
        _wasRolledBack = true;
    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) {
        final MutableList<TransactionId> transactionIds = new MutableList<>();
        for (int i = 0; i < batchSize; ++i) {
            if (i >= _unprocessedTransactions.getCount()) { break; }

            final TransactionId transactionId = _unprocessedTransactions.get(i);
            transactionIds.add(transactionId);
        }
        return transactionIds;
    }

    @Override
    public void markTransactionProcessed(TransactionId transactionId) {
        final int index = _unprocessedTransactions.indexOf(transactionId);
        if (index >= 0) {
            _unprocessedTransactions.remove(index);
        }

        if (_lastTransactionId == null || _lastTransactionId.longValue() < transactionId.longValue()) {
            _lastTransactionId = transactionId;
        }
    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) {
        return _transactionIds.get(transactionHash);
    }

    @Override
    public TransactionId getTransactionId(final SlpTokenId slpTokenId) {
        return _transactionIds.get(slpTokenId);
    }

    @Override
    public Map<Sha256Hash, TransactionId> getTransactionIds(final List<Sha256Hash> transactionHashes) throws ContextException {
        final HashMap<Sha256Hash, TransactionId> transactionIdMap = new HashMap<>();
        for (final Sha256Hash transactionHash : transactionHashes) {
            final TransactionId transactionId = _transactionIds.get(transactionHash);
            transactionIdMap.put(transactionHash, transactionId);
        }
        return transactionIdMap;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) {
        return _transactions.get(transactionId);
    }

    @Override
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final Address address, final Sha256Hash scriptHash, final TransactionId slpTransactionId, final ByteArray memoActionType, final ByteArray memoActionIdentifier) {
        final IndexedOutput indexedOutput = new IndexedOutput(transactionId, outputIndex, amount, scriptType, address, scriptHash, slpTransactionId, memoActionType, memoActionIdentifier);
        _indexedOutputs.add(indexedOutput);
    }

    @Override
    public void indexTransactionInput(final TransactionId transactionId, final Integer inputIndex, final TransactionOutputId transactionOutputId) {
        final IndexedInput indexedInput = new IndexedInput(transactionId, inputIndex, transactionOutputId);
        _indexedInputs.add(indexedInput);
    }

    @Override
    public void close() {
        _wasClosed = true;
    }
}
