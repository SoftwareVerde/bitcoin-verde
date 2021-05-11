package com.softwareverde.bitcoin.server.message.type.thin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class ThinBlockMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public ThinBlockMessage fromBytes(final byte[] bytes) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final ThinBlockMessage thinBlockMessage = new ThinBlockMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.EXTRA_THIN_BLOCK);
        if (protocolMessageHeader == null) { return null; }

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        thinBlockMessage.setBlockHeader(blockHeader);

        final Integer transactionCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (transactionCount > BitcoinConstants.getMaxTransactionCountPerBlock()) { return null; }

        final ImmutableListBuilder<Sha256Hash> transactionHashesListBuilder = new ImmutableListBuilder<Sha256Hash>(transactionCount);
        for (int i = 0; i < transactionCount; ++i) {
            final Sha256Hash transactionHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
            transactionHashesListBuilder.add(transactionHash);
        }
        thinBlockMessage.setTransactionHashes(transactionHashesListBuilder.build());

        final Integer missingTransactionCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (missingTransactionCount > transactionCount) { return null; }

        final ImmutableListBuilder<Transaction> missingTransactionsListBuilder = new ImmutableListBuilder<Transaction>(missingTransactionCount);
        for (int i = 0; i < missingTransactionCount; ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }
            missingTransactionsListBuilder.add(transaction);
        }
        thinBlockMessage.setMissingTransactions(missingTransactionsListBuilder.build());

        if (byteArrayReader.didOverflow()) { return null; }

        return thinBlockMessage;
    }
}
