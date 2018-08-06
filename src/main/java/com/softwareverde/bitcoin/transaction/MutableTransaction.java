package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.util.ConstUtil;
import com.softwareverde.json.Json;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class MutableTransaction implements Transaction {
    protected Long _version = Transaction.VERSION;
    protected final MutableList<MutableTransactionInput> _transactionInputs = new MutableList<MutableTransactionInput>();
    protected final MutableList<MutableTransactionOutput> _transactionOutputs = new MutableList<MutableTransactionOutput>();
    protected LockTime _lockTime = new ImmutableLockTime();

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
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArrayBuilder byteArrayBuilder = transactionDeflater.toByteArrayBuilder(this);
        final byte[] doubleSha256 = BitcoinUtil.sha256(BitcoinUtil.sha256(byteArrayBuilder.build()));
        return MutableSha256Hash.wrap(ByteUtil.reverseEndian(doubleSha256));
    }

    @Override
    public Long getVersion() { return _version; }
    public void setVersion(final Long version) { _version = version; }

    @Override
    public final List<TransactionInput> getTransactionInputs() {
        return ConstUtil.downcastList(_transactionInputs);
    }
    public void addTransactionInput(final TransactionInput transactionInput) {
        _transactionInputs.add(new MutableTransactionInput(transactionInput));
    }
    public void clearTransactionInputs() { _transactionInputs.clear(); }

    public void setTransactionInput(final Integer index, final TransactionInput transactionInput) {
        _transactionInputs.set(index, new MutableTransactionInput(transactionInput));
    }

    @Override
    public final List<TransactionOutput> getTransactionOutputs() {
        return ConstUtil.downcastList(_transactionOutputs);
    }
    public void addTransactionOutput(final TransactionOutput transactionOutput) {
        _transactionOutputs.add(new MutableTransactionOutput(transactionOutput));
    }
    public void clearTransactionOutputs() { _transactionOutputs.clear(); }

    public void setTransactionOutput(final Integer index, final TransactionOutput transactionOutput) {
        _transactionOutputs.set(index, new MutableTransactionOutput(transactionOutput));
    }

    @Override
    public LockTime getLockTime() { return _lockTime; }
    public void setLockTime(final LockTime lockTime) { _lockTime = lockTime; }

    @Override
    public Long getTotalOutputValue() {
        long totalValue = 0L;

        for (final TransactionOutput transactionOutput : _transactionOutputs) {
            totalValue += transactionOutput.getAmount();
        }

        return totalValue;
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
}
