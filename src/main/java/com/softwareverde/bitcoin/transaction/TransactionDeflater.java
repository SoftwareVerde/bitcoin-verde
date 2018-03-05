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
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(transaction.getVersion()), Endian.LITTLE);

        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final List<? extends TransactionInput> transactionInputs = transaction.getTransactionInputs();
        byteArrayBuilder.writeVariableSizedInteger(transactionInputs.getSize());
        for (final TransactionInput transactionInput : transactionInputs) {
            byteArrayBuilder.appendBytes(transactionInputDeflater.toBytes(transactionInput), Endian.BIG);
        }

        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final List<? extends TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        byteArrayBuilder.writeVariableSizedInteger(transactionOutputs.getSize());
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            byteArrayBuilder.appendBytes(transactionOutputDeflater.toBytes(transactionOutput), Endian.BIG);
        }

        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(transaction.getLockTime().getValue()), Endian.LITTLE);

        return byteArrayBuilder;
    }

    public byte[] toBytes(final Transaction transaction) {
        final ByteArrayBuilder byteArrayBuilder = _toByteArrayBuilder(transaction);
        return byteArrayBuilder.build();
    }
}
