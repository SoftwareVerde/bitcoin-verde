package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class BlockInflater {
    protected static class InflatedBlock {
        public final BlockHeader blockHeader;
        public final MutableList<Transaction> transactions;
        public final Integer byteCount;

        public InflatedBlock(final BlockHeader blockHeader, final MutableList<Transaction> transactions, final Integer byteCount) {
            this.blockHeader = blockHeader;
            this.transactions = transactions;
            this.byteCount = byteCount;
        }
    }

    protected InflatedBlock _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final Integer startPosition = byteArrayReader.getPosition();

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        if (blockHeader == null) { return null; }

        final CompactVariableLengthInteger transactionCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! transactionCount.isCanonical()) { return null; }
        if (transactionCount.intValue() > BitcoinConstants.getMaxTransactionCountPerBlock()) { return null; }

        final MutableList<Transaction> transactions = new MutableArrayList<>(transactionCount.intValue());

        for (int i = 0; i < transactionCount.intValue(); ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }

            transactions.add(transaction);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        final Integer endPosition = byteArrayReader.getPosition();
        final Integer byteCount = (endPosition - startPosition);

        return new InflatedBlock(blockHeader, transactions, byteCount);
    }

    protected MutableBlock _toMutableBlock(final InflatedBlock inflatedBlock) {
        final MutableBlock mutableBlock = new MutableBlock(inflatedBlock.blockHeader, inflatedBlock.transactions);
        mutableBlock.cacheByteCount(inflatedBlock.byteCount);
        return mutableBlock;
    }

    public MutableBlock fromBytes(final ByteArrayReader byteArrayReader) {
        if (byteArrayReader == null) { return null; }

        final InflatedBlock inflatedBlock = _fromByteArrayReader(byteArrayReader);
        return _toMutableBlock(inflatedBlock);
    }

    public MutableBlock fromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }

        final InflatedBlock inflatedBlock = _fromByteArrayReader(new ByteArrayReader(byteArray));
        return _toMutableBlock(inflatedBlock);
    }

    public MutableBlock fromBytes(final byte[] bytes) {
        if (bytes == null) { return null; }

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        final InflatedBlock inflatedBlock = _fromByteArrayReader(byteArrayReader);
        return _toMutableBlock(inflatedBlock);
    }
}
