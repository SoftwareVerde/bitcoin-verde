package com.softwareverde.bitcoin.server.message.type.thin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class ExtraThinBlockMessageInflater extends BitcoinProtocolMessageInflater {
    protected final BlockHeaderInflaters _blockHeaderInflaters;
    protected final TransactionInflaters _transactionInflaters;

    public ExtraThinBlockMessageInflater(final BlockHeaderInflaters blockHeaderInflaters, final TransactionInflaters transactionInflaters) {
        _blockHeaderInflaters = blockHeaderInflaters;
        _transactionInflaters = transactionInflaters;
    }

    @Override
    public ExtraThinBlockMessage fromBytes(final byte[] bytes) {
        final ExtraThinBlockMessage extraThinBlockMessage = new ExtraThinBlockMessage(_blockHeaderInflaters, _transactionInflaters);
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.EXTRA_THIN_BLOCK);
        if (protocolMessageHeader == null) { return null; }

        final BlockHeaderInflater blockHeaderInflater = _blockHeaderInflaters.getBlockHeaderInflater();
        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        extraThinBlockMessage.setBlockHeader(blockHeader);

        final CompactVariableLengthInteger transactionCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! transactionCount.isCanonical()) { return null; }
        if (transactionCount.intValue() > BitcoinConstants.getMaxTransactionCountPerBlock()) { return null; }

        final ImmutableListBuilder<ByteArray> transactionShortHashesListBuilder = new ImmutableListBuilder<>(transactionCount.intValue());
        for (int i = 0; i < transactionCount.intValue(); ++i) {
            final ByteArray transactionShortHash = MutableByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));
            transactionShortHashesListBuilder.add(transactionShortHash);
        }
        extraThinBlockMessage.setTransactionHashes(transactionShortHashesListBuilder.build());

        final CompactVariableLengthInteger missingTransactionCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! missingTransactionCount.isCanonical()) { return null; }
        if (missingTransactionCount.intValue() > transactionCount.intValue()) { return null; }

        final TransactionInflater transactionInflater = _transactionInflaters.getTransactionInflater();
        final ImmutableListBuilder<Transaction> missingTransactionsListBuilder = new ImmutableListBuilder<>(missingTransactionCount.intValue());
        for (int i = 0; i < missingTransactionCount.intValue(); ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }
            missingTransactionsListBuilder.add(transaction);
        }
        extraThinBlockMessage.setMissingTransactions(missingTransactionsListBuilder.build());

        if (byteArrayReader.didOverflow()) { return null; }

        return extraThinBlockMessage;
    }
}
