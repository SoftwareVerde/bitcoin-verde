package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.ImmutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.ImmutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

import java.util.ArrayList;
import java.util.List;

public class MutableTransaction implements Transaction {
    protected Integer _version;
    protected Boolean _hasWitnessData = false;
    protected final List<TransactionInput> _transactionInputs = new ArrayList<TransactionInput>();
    protected final List<TransactionOutput> _transactionOutputs = new ArrayList<TransactionOutput>();
    protected LockTime _lockTime = new ImmutableLockTime();

    /**
     * NOTE: Math with Satoshis
     *  The maximum number of satoshis is 210,000,000,000,000, which is less than the value a Java Long can hold.
     *  Therefore, using BigInteger is not be necessary any transaction calculation.
     */

    protected byte[] _getBytes() {
        final byte[] versionBytes = new byte[4];
        ByteUtil.setBytes(versionBytes, ByteUtil.integerToBytes(_version));

        final byte[] lockTimeBytes = new byte[4];
        ByteUtil.setBytes(lockTimeBytes, _lockTime.getBytes());

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

    @Override
    public Hash calculateSha256Hash() {
        return new MutableHash(ByteUtil.reverseBytes(BitcoinUtil.sha256(BitcoinUtil.sha256(_getBytes()))));
    }

    @Override
    public Integer getVersion() { return _version; }
    public void setVersion(final Integer version) { _version = version; }

    @Override
    public Boolean hasWitnessData() { return _hasWitnessData; }
    public void setHasWitnessData(final Boolean hasWitnessData) { _hasWitnessData = hasWitnessData; }

    @Override
    public final List<TransactionInput> getTransactionInputs() {
        final List<TransactionInput> transactionInputs = new ArrayList<TransactionInput>(_transactionInputs.size());
        for (final TransactionInput transactionInput : _transactionInputs) {
            transactionInputs.add(new ImmutableTransactionInput(transactionInput));
        }
        return transactionInputs;
    }
    public void addTransactionInput(final TransactionInput transactionInput) { _transactionInputs.add(new ImmutableTransactionInput(transactionInput)); }
    public void clearTransactionInputs() { _transactionInputs.clear(); }

    @Override
    public final List<TransactionOutput> getTransactionOutputs() {
        final List<TransactionOutput> transactionOutputs = new ArrayList<TransactionOutput>(_transactionOutputs.size());
        for (final TransactionOutput transactionOutput : _transactionOutputs) {
            transactionOutputs.add(new ImmutableTransactionOutput(transactionOutput));
        }
        return transactionOutputs;
    }
    public void addTransactionOutput(final TransactionOutput transactionOutput) { _transactionOutputs.add(new ImmutableTransactionOutput(transactionOutput)); }
    public void clearTransactionOutputs() { _transactionOutputs.clear(); }

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

    @Override
    public byte[] getBytes() {
        return _getBytes();
    }
}
