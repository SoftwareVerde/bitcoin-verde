package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class FakeAtomicTransactionOutputIndexerContext implements com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext {
    protected final HashMap<Address, AddressId> _addresses = new HashMap<Address, AddressId>(0);
    protected final HashMap<Sha256Hash, TransactionId> _transactionIds = new HashMap<Sha256Hash, TransactionId>(0);
    protected final HashMap<TransactionId, Transaction> _transactions = new HashMap<TransactionId, Transaction>(0);

    protected final MutableList<Address> _storedAddresses = new MutableList<Address>(0);
    protected final MutableList<TransactionId> _unprocessedTransactions = new MutableList<TransactionId>(0);
    protected final MutableList<IndexedOutput> _indexedOutputs = new MutableList<IndexedOutput>(0);
    protected final MutableList<IndexedInput> _indexedInputs = new MutableList<IndexedInput>(0);

    protected Boolean _wasCommitted = false;
    protected Boolean _wasRolledBack = false;
    protected Boolean _wasClosed = false;

    public void addAddress(final Address address) {
        final AddressId addressId = AddressId.wrap(_addresses.size() + 1L);
        _addresses.put(address, addressId);
    }

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
        return new ImmutableList<TransactionId>(_transactionIds.values());
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
    public void commitDatabaseTransaction() {
        _wasCommitted = true;
    }

    @Override
    public void rollbackDatabaseTransaction() {
        _wasRolledBack = true;
    }

    @Override
    public AddressId getAddressId(final Address address) {
        return _addresses.get(address);
    }

    @Override
    public AddressId storeAddress(final Address address) {
        _storedAddresses.add(address);

        if (_addresses.containsKey(address)) {
            return _addresses.get(address);
        }

        final AddressId addressId = AddressId.wrap(_addresses.size() + 1L);
        _addresses.put(address, addressId);
        return addressId;
    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) {
        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>();
        for (int i = 0; i < batchSize; ++i) {
            if (i >= _unprocessedTransactions.getCount()) { break; }

            final TransactionId transactionId = _unprocessedTransactions.get(i);
            transactionIds.add(transactionId);
        }
        return transactionIds;
    }

    @Override
    public void dequeueTransactionsForProcessing(final List<TransactionId> transactionIds) {
        for (final TransactionId transactionId : transactionIds) {
            final int index = _unprocessedTransactions.indexOf(transactionId);
            if (index >= 0) {
                _unprocessedTransactions.remove(index);
            }
        }
    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws ContextException {
        return _transactionIds.get(transactionHash);
    }

    @Override
    public TransactionId getTransactionId(final SlpTokenId slpTokenId) {
        return _transactionIds.get(slpTokenId);
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) {
        return _transactions.get(transactionId);
    }

    @Override
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) {
        final IndexedOutput indexedOutput = new IndexedOutput(transactionId, outputIndex, amount, scriptType, addressId, slpTransactionId);
        _indexedOutputs.add(indexedOutput);
    }

    @Override
    public void indexTransactionInput(final TransactionId transactionId, final Integer inputIndex, final AddressId addressId) throws ContextException {
        final IndexedInput indexedInput = new IndexedInput(transactionId, inputIndex, addressId);
        _indexedInputs.add(indexedInput);
    }

    @Override
    public void close() throws ContextException {
        _wasClosed = true;
    }
}
