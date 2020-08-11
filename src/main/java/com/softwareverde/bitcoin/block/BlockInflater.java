package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.ByteUtil;

public class BlockInflater {
    public static final Integer MAX_BYTE_COUNT = (int) (32L * ByteUtil.Unit.Si.MEGABYTES);
    public static final Integer MAX_TRANSACTION_COUNT = (BlockInflater.MAX_BYTE_COUNT / TransactionInflater.MIN_BYTE_COUNT);

    protected MutableBlock _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        if (blockHeader == null) { return null; }

        final int transactionCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (transactionCount > MAX_TRANSACTION_COUNT) { return null; }

        final MutableList<Transaction> transactions = new MutableList<Transaction>(transactionCount);

        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }

            transactions.add(transaction);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return new MutableBlock(blockHeader, transactions);
    }

    public MutableBlock fromBytes(final ByteArrayReader byteArrayReader) {
        if (byteArrayReader == null) { return null; }

        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableBlock fromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }

        return _fromByteArrayReader(new ByteArrayReader(byteArray));
    }

    public MutableBlock fromBytes(final byte[] bytes) {
        if (bytes == null) { return null; }

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}
