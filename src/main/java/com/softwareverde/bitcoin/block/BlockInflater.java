package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;

public class BlockInflater {
    protected MutableBlock _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final Integer startPosition = byteArrayReader.getPosition();

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        if (blockHeader == null) { return null; }

        final int transactionCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (transactionCount > BitcoinConstants.getMaxTransactionCountPerBlock()) { return null; }

        final MutableList<Transaction> transactions = new MutableList<Transaction>(transactionCount);

        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }

            transactions.add(transaction);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        final Integer endPosition = byteArrayReader.getPosition();
        final Integer byteCount = (endPosition - startPosition);

        final MutableBlock mutableBlock = new MutableBlock(blockHeader, transactions);
        mutableBlock.cacheByteCount(byteCount);

        return mutableBlock;
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
