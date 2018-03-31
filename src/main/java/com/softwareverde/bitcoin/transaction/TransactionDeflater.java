package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.type.bytearray.FragmentedBytes;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.list.List;

public class TransactionDeflater {
    protected void _toFragmentedBytes(final Transaction transaction, final ByteArrayBuilder headBytesBuilder, final ByteArrayBuilder tailBytesBuilder) {
        final byte[] versionBytes = new byte[4];
        ByteUtil.setBytes(versionBytes, ByteUtil.integerToBytes(transaction.getVersion()));

        final byte[] lockTimeBytes = new byte[4];
        ByteUtil.setBytes(lockTimeBytes, transaction.getLockTime().getBytes());

        headBytesBuilder.appendBytes(versionBytes, Endian.LITTLE);

        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        headBytesBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionInputs.getSize()), Endian.BIG);
        int transactionInputIndex = 0;
        for (final TransactionInput transactionInput : transactionInputs) {
            if (transactionInputIndex == 0) {
                final FragmentedBytes fragmentedTransactionInputBytes = transactionInputDeflater.fragmentTransactionInput(transactionInput);
                headBytesBuilder.appendBytes(fragmentedTransactionInputBytes.headBytes, Endian.BIG);
                tailBytesBuilder.appendBytes(fragmentedTransactionInputBytes.tailBytes, Endian.BIG);
            }
            else {
                tailBytesBuilder.appendBytes(transactionInputDeflater.toBytes(transactionInput), Endian.BIG);
            }
            transactionInputIndex += 1;
        }

        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        tailBytesBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionOutputs.getSize()), Endian.BIG);
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            tailBytesBuilder.appendBytes(transactionOutputDeflater.toBytes(transactionOutput), Endian.BIG);
        }

        tailBytesBuilder.appendBytes(lockTimeBytes, Endian.LITTLE);
    }

    public ByteArrayBuilder toByteArrayBuilder(final Transaction transaction) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        _toFragmentedBytes(transaction, byteArrayBuilder, byteArrayBuilder);
        return byteArrayBuilder;
    }

    public byte[] toBytes(final Transaction transaction) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        _toFragmentedBytes(transaction, byteArrayBuilder, byteArrayBuilder);
        return byteArrayBuilder.build();
    }

    public Integer getByteCount(final Transaction transaction) {
        final Integer versionByteCount = 4;

        final Integer transactionInputsByteCount;
        {
            final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();

            Integer byteCount = 0;
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
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
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            byteCount += ByteUtil.variableLengthIntegerToBytes(transactionOutputs.getSize()).length;
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                byteCount += transactionOutputDeflater.getByteCount(transactionOutput);
            }
            transactionOutputsByteCount = byteCount;
        }

        final Integer lockTimeByteCount = 4;

        return (versionByteCount + transactionInputsByteCount + transactionOutputsByteCount + lockTimeByteCount);
    }

    public FragmentedBytes fragmentTransaction(final Transaction transaction) {
        final ByteArrayBuilder headBytesBuilder = new ByteArrayBuilder();
        final ByteArrayBuilder tailBytesBuilder = new ByteArrayBuilder();

        _toFragmentedBytes(transaction, headBytesBuilder, tailBytesBuilder);

        return new FragmentedBytes(headBytesBuilder.build(), tailBytesBuilder.build());
    }
}
