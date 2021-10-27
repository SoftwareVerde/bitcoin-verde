package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.transaction.coinbase.MutableCoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.util.ConstUtil;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class MutableTransaction implements Transaction {
    /**
     * NOTE: Math with Satoshis
     *  The maximum number of satoshis is 210,000,000,000,000, which is less than the value a Java Long can hold.
     *  Therefore, using BigInteger is not be necessary any non-multiplicative transaction calculation.
     */

    protected static final TransactionHasher DEFAULT_TRANSACTION_HASHER = new TransactionHasher();
    protected static final TransactionDeflater DEFAULT_TRANSACTION_DEFLATER = new TransactionDeflater();
    protected static final AddressInflater DEFAULT_ADDRESS_INFLATER = new AddressInflater();

    protected final TransactionHasher _transactionHasher;
    protected final TransactionDeflater _transactionDeflater;
    protected final AddressInflater _addressInflater;

    protected Long _version = Transaction.VERSION;
    protected final MutableList<TransactionInput> _transactionInputs = new MutableList<>();
    protected final MutableList<TransactionOutput> _transactionOutputs = new MutableList<>();
    protected LockTime _lockTime = new ImmutableLockTime();

    protected Integer _cachedByteCount = null;
    protected Sha256Hash _cachedHash = null;
    protected Integer _cachedHashCode = null;

    protected void _invalidateCachedProperties() {
        _cachedByteCount = null;
        _cachedHash = null;
        _cachedHashCode = null;
    }

    protected Integer _calculateByteCount() {
        return _transactionDeflater.getByteCount(this);
    }

    protected void cacheByteCount(final Integer byteCount) {
        _cachedByteCount = byteCount;
    }

    protected MutableTransaction(final TransactionHasher transactionHasher, final TransactionDeflater transactionDeflater, final AddressInflater addressInflater) {
        _transactionHasher = transactionHasher;
        _transactionDeflater = transactionDeflater;
        _addressInflater = addressInflater;
    }

    public MutableTransaction() {
        _transactionHasher = DEFAULT_TRANSACTION_HASHER;
        _transactionDeflater = DEFAULT_TRANSACTION_DEFLATER;
        _addressInflater = DEFAULT_ADDRESS_INFLATER;
    }

    public MutableTransaction(final Transaction transaction) {
        _transactionHasher = DEFAULT_TRANSACTION_HASHER;
        _transactionDeflater = DEFAULT_TRANSACTION_DEFLATER;
        _addressInflater = DEFAULT_ADDRESS_INFLATER;

        _version = transaction.getVersion();

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            _transactionInputs.add(new MutableTransactionInput(transactionInput));
        }

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            _transactionOutputs.add(new MutableTransactionOutput(transactionOutput));
        }

        _lockTime = transaction.getLockTime().asConst();
    }

    @Override
    public Sha256Hash getHash() {
        final Sha256Hash cachedHash = _cachedHash;
        if (cachedHash != null) { return cachedHash; }

        final Sha256Hash hash = _transactionHasher.hashTransaction(this);
        _cachedHash = hash;
        return hash;
    }

    @Override
    public Long getVersion() { return _version; }

    public void setVersion(final Long version) {
        _version = version;
        _invalidateCachedProperties();
    }

    @Override
    public final List<TransactionInput> getTransactionInputs() {
        return ConstUtil.downcastList(_transactionInputs);
    }

    public void addTransactionInput(final TransactionInput transactionInput) {
        _transactionInputs.add(transactionInput.asConst());
        _invalidateCachedProperties();
    }

    public void clearTransactionInputs() {
        _transactionInputs.clear();
        _invalidateCachedProperties();
    }

    public void setTransactionInput(final Integer index, final TransactionInput transactionInput) {
        _transactionInputs.set(index, transactionInput.asConst());
        _invalidateCachedProperties();
    }

    @Override
    public final List<TransactionOutput> getTransactionOutputs() {
        return _transactionOutputs;
    }

    public void addTransactionOutput(final TransactionOutput transactionOutput) {
        _transactionOutputs.add(transactionOutput.asConst());
        _invalidateCachedProperties();
    }

    public void clearTransactionOutputs() {
        _transactionOutputs.clear();
        _invalidateCachedProperties();
    }

    public void setTransactionOutput(final Integer index, final TransactionOutput transactionOutput) {
        _transactionOutputs.set(index, transactionOutput.asConst());
        _invalidateCachedProperties();

    }

    @Override
    public LockTime getLockTime() { return _lockTime; }

    public void setLockTime(final LockTime lockTime) {
        _lockTime = lockTime;
        _invalidateCachedProperties();
    }

    @Override
    public Long getTotalOutputValue() {
        long totalValue = 0L;

        for (final TransactionOutput transactionOutput : _transactionOutputs) {
            totalValue += transactionOutput.getAmount();
        }

        return totalValue;
    }

    @Override
    public Boolean matches(final BloomFilter bloomFilter) {
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, _addressInflater);
        return transactionBloomFilterMatcher.shouldInclude(this);
    }

    @Override
    public MutableCoinbaseTransaction asCoinbase() {
        if (! Transaction.isCoinbaseTransaction(this)) { return null; }

        return new MutableCoinbaseTransaction(this);
    }

    @Override
    public Integer getByteCount() {
        final Integer cachedByteCount = _cachedByteCount;
        if (cachedByteCount != null) { return cachedByteCount; }

        final Integer byteCount = _calculateByteCount();
        _cachedByteCount = byteCount;
        return byteCount;
    }

    @Override
    public ImmutableTransaction asConst() {
        return new ImmutableTransaction(this);
    }

    @Override
    public Json toJson() {
        return _transactionDeflater.toJson(this);
    }

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final int hashCode = _transactionHasher.hashTransaction(this).hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof Transaction)) { return false; }
        return Util.areEqual(this.getHash(), ((Transaction) object).getHash());
    }
}
