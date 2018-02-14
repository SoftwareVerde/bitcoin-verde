package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class Transaction {
    public static final Long MAX_LOCK_TIME = 0xFFFFFFFFL;

    protected Integer _version;
    protected Boolean _hasWitnessData = false;
    protected final List<TransactionInput> _transactionInputs = new ArrayList<TransactionInput>();
    protected final List<TransactionOutput> _transactionOutputs = new ArrayList<TransactionOutput>();
    protected Long _lockTime = null;

    public Integer getVersion() { return _version; }
    public void setVersion(final Integer version) { _version = version; }

    public Boolean hasWitnessData() { return _hasWitnessData; }
    public void setHasWitnessData(final Boolean hasWitnessData) { _hasWitnessData = hasWitnessData; }

    public final List<TransactionInput> getTransactionInputs() {
        final List<TransactionInput> transactionInputs = new ArrayList<TransactionInput>(_transactionInputs.size());
        for (final TransactionInput transactionInput : _transactionInputs) {
            transactionInputs.add(transactionInput.copy());
        }
        return transactionInputs;
    }
    public void addTransactionInput(final TransactionInput transactionInput) { _transactionInputs.add(transactionInput.copy()); }
    public void clearTransactionInputs() { _transactionInputs.clear(); }

    public final List<TransactionOutput> getTransactionOutputs() {
        final List<TransactionOutput> transactionOutputs = new ArrayList<TransactionOutput>(_transactionOutputs.size());
        for (final TransactionOutput transactionOutput : _transactionOutputs) {
            transactionOutputs.add(transactionOutput.copy());
        }
        return transactionOutputs;
    }
    public void addTransactionOutput(final TransactionOutput transactionOutput) { _transactionOutputs.add(transactionOutput.copy()); }
    public void clearTransactionOutputs() { _transactionOutputs.clear(); }

    public Long getLockTime() { return _lockTime; }
    public void setLockTime(final Long lockTime) { _lockTime = lockTime; }

    public Integer getByteCount() {
        final Integer versionByteCount = 4;

        final Integer transactionInputsByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(_transactionInputs.size()).length;
            for (final TransactionInput transactionInput : _transactionInputs) {
                byteCount += transactionInput.getByteCount();
            }
            transactionInputsByteCount = byteCount;
        }

        final Integer transactionOutputsByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(_transactionOutputs.size()).length;
            for (final TransactionOutput transactionOutput : _transactionOutputs) {
                byteCount += transactionOutput.getByteCount();
            }
            transactionOutputsByteCount = byteCount;
        }

        final Integer lockTimeByteCount = 4;

        return (versionByteCount + transactionInputsByteCount + transactionOutputsByteCount + lockTimeByteCount);
    }

    public byte[] getBytes() {
        final byte[] versionBytes = new byte[4];
        ByteUtil.setBytes(versionBytes, ByteUtil.integerToBytes(_version));

        final byte[] lockTimeBytes = new byte[4];
        ByteUtil.setBytes(lockTimeBytes, ByteUtil.integerToBytes(_lockTime.intValue()));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(versionBytes, Endian.LITTLE);

        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_transactionInputs.size()), Endian.BIG);
        for (final TransactionInput transactionInput : _transactionInputs) {
            byteArrayBuilder.appendBytes(transactionInput.getBytes(), Endian.BIG);
        }

        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_transactionOutputs.size()), Endian.BIG);
        for (final TransactionOutput transactionOutput : _transactionOutputs) {
            byteArrayBuilder.appendBytes(transactionOutput.getBytes(), Endian.BIG);
        }

        byteArrayBuilder.appendBytes(lockTimeBytes, Endian.LITTLE);

        return byteArrayBuilder.build();
    }

    public Transaction copy() {
        final Transaction transaction = new Transaction();
        transaction._version = _version;
        transaction._hasWitnessData = _hasWitnessData;
        for (final TransactionInput transactionInput : _transactionInputs) {
            transaction._transactionInputs.add(transactionInput.copy());
        }
        for (final TransactionOutput transactionOutput : _transactionOutputs) {
            transaction._transactionOutputs.add(transactionOutput.copy());
        }
        transaction._lockTime = _lockTime;
        return transaction;
    }
}
