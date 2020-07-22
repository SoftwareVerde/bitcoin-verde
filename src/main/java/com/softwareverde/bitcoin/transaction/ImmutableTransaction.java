package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.coinbase.ImmutableCoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.ImmutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.output.ImmutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.util.ConstUtil;
import com.softwareverde.cryptography.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class ImmutableTransaction implements ConstTransaction {
    protected final ImmutableSha256Hash _hash;
    protected final Long _version;
    protected final List<ImmutableTransactionInput> _transactionInputs;
    protected final List<ImmutableTransactionOutput> _transactionOutputs;
    protected final ImmutableLockTime _lockTime;

    protected Integer _cachedHashCode;

    public ImmutableTransaction(final Transaction transaction) {
        _hash = transaction.getHash().asConst();
        _version = transaction.getVersion();
        _lockTime = transaction.getLockTime().asConst();

        _transactionInputs = ImmutableListBuilder.newConstListOfConstItems(transaction.getTransactionInputs());
        _transactionOutputs = ImmutableListBuilder.newConstListOfConstItems(transaction.getTransactionOutputs());
    }

    @Override
    public ImmutableSha256Hash getHash() {
        return _hash;
    }

    @Override
    public Long getVersion() { return _version; }

    @Override
    public final List<TransactionInput> getTransactionInputs() {
        return ConstUtil.downcastList(_transactionInputs);
    }

    @Override
    public final List<TransactionOutput> getTransactionOutputs() {
        return ConstUtil.downcastList(_transactionOutputs);
    }

    @Override
    public ImmutableLockTime getLockTime() { return _lockTime; }

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
    public ImmutableCoinbaseTransaction asCoinbase() {
        if (! Transaction.isCoinbaseTransaction(this)) { return null; }

        return new ImmutableCoinbaseTransaction(this);
    }

    @Override
    public ImmutableTransaction asConst() {
        return this;
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

        _cachedHashCode = _hash.hashCode();
        return _cachedHashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof Transaction)) { return false; }
        return Util.areEqual(this.getHash(), ((Transaction) object).getHash());
    }
}
