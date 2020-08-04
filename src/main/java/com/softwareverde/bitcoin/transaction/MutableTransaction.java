package com.softwareverde.bitcoin.transaction;

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
    protected Long _version = Transaction.VERSION;
    protected final MutableList<TransactionInput> _transactionInputs = new MutableList<TransactionInput>();
    protected final MutableList<TransactionOutput> _transactionOutputs = new MutableList<TransactionOutput>();
    protected LockTime _lockTime = new ImmutableLockTime();

    protected Integer _cachedHashCode = null;

    /**
     * NOTE: Math with Satoshis
     *  The maximum number of satoshis is 210,000,000,000,000, which is less than the value a Java Long can hold.
     *  Therefore, using BigInteger is not be necessary any non-multiplicative transaction calculation.
     */

    public MutableTransaction() { }

    public MutableTransaction(final Transaction transaction) {
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
        final TransactionHasher transactionHasher = new TransactionHasher();
        return transactionHasher.hashTransaction(this);
    }

    @Override
    public Long getVersion() { return _version; }

    public void setVersion(final Long version) {
        _version = version;
        _cachedHashCode = null;
    }

    @Override
    public final List<TransactionInput> getTransactionInputs() {
        return ConstUtil.downcastList(_transactionInputs);
    }

    public void addTransactionInput(final TransactionInput transactionInput) {
        _transactionInputs.add(transactionInput.asConst());
        _cachedHashCode = null;
    }

    public void clearTransactionInputs() {
        _transactionInputs.clear();
        _cachedHashCode = null;
    }

    public void setTransactionInput(final Integer index, final TransactionInput transactionInput) {
        _transactionInputs.set(index, transactionInput.asConst());
        _cachedHashCode = null;
    }

    @Override
    public final List<TransactionOutput> getTransactionOutputs() {
        return _transactionOutputs;
    }

    public void addTransactionOutput(final TransactionOutput transactionOutput) {
        _transactionOutputs.add(transactionOutput.asConst());
        _cachedHashCode = null;
    }

    public void clearTransactionOutputs() {
        _transactionOutputs.clear();
        _cachedHashCode = null;
    }

    public void setTransactionOutput(final Integer index, final TransactionOutput transactionOutput) {
        _transactionOutputs.set(index, transactionOutput.asConst());
        _cachedHashCode = null;
    }

    @Override
    public LockTime getLockTime() { return _lockTime; }

    public void setLockTime(final LockTime lockTime) {
        _lockTime = lockTime;
        _cachedHashCode = null;
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
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter);
        return transactionBloomFilterMatcher.shouldInclude(this);
    }

    @Override
    public MutableCoinbaseTransaction asCoinbase() {
        if (! Transaction.isCoinbaseTransaction(this)) { return null; }

        return new MutableCoinbaseTransaction(this);
    }

    @Override
    public ImmutableTransaction asConst() {
        return new ImmutableTransaction(this);
    }

    @Override
    public Json toJson() {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        return transactionDeflater.toJson(this);
    }

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final TransactionHasher transactionHasher = new TransactionHasher();
        final Integer hashCode = transactionHasher.hashTransaction(this).hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof Transaction)) { return false; }
        return Util.areEqual(this.getHash(), ((Transaction) object).getHash());
    }
}
