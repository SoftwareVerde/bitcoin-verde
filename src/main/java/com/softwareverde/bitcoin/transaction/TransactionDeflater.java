package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.list.List;

public class TransactionDeflater {
    protected ByteArrayBuilder _toByteArrayBuilder(final Transaction transaction) {
        final byte[] versionBytes = new byte[4];
        ByteUtil.setBytes(versionBytes, ByteUtil.integerToBytes(transaction.getVersion()));

        final byte[] lockTimeBytes = new byte[4];
        ByteUtil.setBytes(lockTimeBytes, transaction.getLockTime().getBytes());

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(versionBytes, Endian.LITTLE);

        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final List<? extends TransactionInput> transactionInputs = transaction.getTransactionInputs();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionInputs.getSize()), Endian.BIG);
        for (final TransactionInput transactionInput : transactionInputs) {
            byteArrayBuilder.appendBytes(transactionInputDeflater.toBytes(transactionInput), Endian.BIG);
        }

        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final List<? extends TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionOutputs.getSize()), Endian.BIG);
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            byteArrayBuilder.appendBytes(transactionOutputDeflater.toBytes(transactionOutput), Endian.BIG);
        }

        byteArrayBuilder.appendBytes(lockTimeBytes, Endian.LITTLE);

        return byteArrayBuilder;
    }

    public ByteArrayBuilder toByteArrayBuilder(final Transaction transaction) {
        return _toByteArrayBuilder(transaction);
    }

    public byte[] toBytes(final Transaction transaction) {
        return _toByteArrayBuilder(transaction).build();
    }

    public Integer getByteCount(final Transaction transaction) {
        final Integer versionByteCount = 4;

        final Integer transactionInputsByteCount;
        {
            final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();

            Integer byteCount = 0;
            final List<? extends TransactionInput> transactionInputs = transaction.getTransactionInputs();
            byteCount += ByteUtil.variableLengthIntegerToBytes(transactionInputs.getSize()).length;
            for (final TransactionInput transactionInput : transactionInputs) {
                byteCount += transactionInputDeflater.getByteCount(transactionInput);
            }
            transactionInputsByteCount = byteCount;
        }

        final Integer transactionOutputsByteCount;
        {
            final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();

            Integer byteCount = 0;
            final List<? extends TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            byteCount += ByteUtil.variableLengthIntegerToBytes(transactionOutputs.getSize()).length;
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                byteCount += transactionOutputDeflater.getByteCount(transactionOutput);
            }
            transactionOutputsByteCount = byteCount;
        }

        final Integer lockTimeByteCount = 4;

        return (versionByteCount + transactionInputsByteCount + transactionOutputsByteCount + lockTimeByteCount);
    }
}
