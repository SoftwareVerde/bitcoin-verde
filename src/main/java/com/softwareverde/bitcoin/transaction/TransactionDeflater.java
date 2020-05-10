package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.bytearray.FragmentedBytes;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class TransactionDeflater {
    protected void _toFragmentedBytes(final Transaction transaction, final ByteArrayBuilder headBytesBuilder, final ByteArrayBuilder tailBytesBuilder) {
        final byte[] versionBytes = new byte[4];
        ByteUtil.setBytes(versionBytes, ByteUtil.integerToBytes(transaction.getVersion()));

        final byte[] lockTimeBytes = new byte[4];
        final LockTime lockTime = transaction.getLockTime();
        ByteUtil.setBytes(MutableByteArray.wrap(lockTimeBytes), lockTime.getBytes());

        headBytesBuilder.appendBytes(versionBytes, Endian.LITTLE);

        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        headBytesBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionInputs.getCount()), Endian.BIG);
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
        tailBytesBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionOutputs.getCount()), Endian.BIG);
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            tailBytesBuilder.appendBytes(transactionOutputDeflater.toBytes(transactionOutput), Endian.BIG);
        }

        tailBytesBuilder.appendBytes(lockTimeBytes, Endian.LITTLE);
    }

    protected byte[] _toBytes(final Transaction transaction) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        _toFragmentedBytes(transaction, byteArrayBuilder, byteArrayBuilder);
        return byteArrayBuilder.build();
    }

    public ByteArrayBuilder toByteArrayBuilder(final Transaction transaction) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        _toFragmentedBytes(transaction, byteArrayBuilder, byteArrayBuilder);
        return byteArrayBuilder;
    }

    public ByteArray toBytes(final Transaction transaction) {
        return MutableByteArray.wrap(_toBytes(transaction));
    }

    public Integer getByteCount(final Transaction transaction) {
        final Integer versionByteCount = 4;

        final Integer transactionInputsByteCount;
        {
            final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();

            Integer byteCount = 0;
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
            byteCount += ByteUtil.variableLengthIntegerToBytes(transactionInputs.getCount()).length;
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
            byteCount += ByteUtil.variableLengthIntegerToBytes(transactionOutputs.getCount()).length;
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

    public Json toJson(final Transaction transaction) {
        final Json json = new Json();

        json.put("version", transaction.getVersion());

        json.put("hash", transaction.getHash());

        final Json inputsJson = new Json();
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            inputsJson.add(transactionInput);
        }
        json.put("inputs", inputsJson);


        final Json outputsJson = new Json();
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            outputsJson.add(transactionOutput);
        }
        json.put("outputs", outputsJson);
        json.put("lockTime", transaction.getLockTime());

        // json.put("bytes", HexUtil.toHexString(_toBytes(transaction)));

        return json;
    }
}
