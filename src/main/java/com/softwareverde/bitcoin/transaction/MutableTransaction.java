package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class MutableTransaction implements Transaction {
    protected Integer _version = Transaction.VERSION;
    protected Boolean _hasWitnessData = false;
    protected final MutableList<TransactionInput> _transactionInputs = new MutableList<TransactionInput>();
    protected final MutableList<TransactionOutput> _transactionOutputs = new MutableList<TransactionOutput>();
    protected LockTime _lockTime = new ImmutableLockTime();

    /**
     * NOTE: Math with Satoshis
     *  The maximum number of satoshis is 210,000,000,000,000, which is less than the value a Java Long can hold.
     *  Therefore, using BigInteger is not be necessary any transaction calculation.
     */

    public MutableTransaction() { }

    public MutableTransaction(final Transaction transaction) {
        _version = transaction.getVersion();
        _hasWitnessData = transaction.hasWitnessData();

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            _transactionInputs.add(transactionInput.asConst());
        }

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            _transactionOutputs.add(transactionOutput.asConst());
        }

        _lockTime = transaction.getLockTime().asConst();
    }

    @Override
    public Hash getHash() {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArrayBuilder byteArrayBuilder = transactionDeflater.toByteArrayBuilder(this);
        final byte[] doubleSha256 = BitcoinUtil.sha256(BitcoinUtil.sha256(byteArrayBuilder.build()));
        return MutableHash.wrap(ByteUtil.reverseEndian(doubleSha256));
    }

    @Override
    public Integer getVersion() { return _version; }
    public void setVersion(final Integer version) { _version = version; }

    @Override
    public Boolean hasWitnessData() { return _hasWitnessData; }
    public void setHasWitnessData(final Boolean hasWitnessData) { _hasWitnessData = hasWitnessData; }

    @Override
    public final List<TransactionInput> getTransactionInputs() {
        return _transactionInputs;
    }
    public void addTransactionInput(final TransactionInput transactionInput) {
        _transactionInputs.add(transactionInput);
    }
    public void clearTransactionInputs() { _transactionInputs.clear(); }

    public void setTransactionInput(final Integer index, final TransactionInput transactionInput) {
        _transactionInputs.set(index, transactionInput.asConst());
    }

    @Override
    public final List<TransactionOutput> getTransactionOutputs() {
        return _transactionOutputs;
    }
    public void addTransactionOutput(final TransactionOutput transactionOutput) {
        _transactionOutputs.add(transactionOutput);
    }
    public void clearTransactionOutputs() { _transactionOutputs.clear(); }

    public void setTransactionOutput(final Integer index, final TransactionOutput transactionOutput) {
        _transactionOutputs.set(index, transactionOutput.asConst());
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
}
