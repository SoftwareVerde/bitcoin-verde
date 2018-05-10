package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.list.mutable.MutableList;

public class BlockInflater {
    protected MutableBlock _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        if (blockHeader == null) { return null; }

        final Long transactionCount = byteArrayReader.readVariableSizedInteger();
        final MutableList<Transaction> transactions = new MutableList<Transaction>(transactionCount.intValue());

        for (long i=0; i<transactionCount; ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }

            transactions.add(transaction);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return new MutableBlock(blockHeader, transactions);
    }

    public MutableBlock fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableBlock fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}
