package com.softwareverde.bitcoin.server.message.type.thin.transaction;

import com.softwareverde.bitcoin.inflater.TransactionInflaters;
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

public class ThinTransactionsMessageInflater extends BitcoinProtocolMessageInflater {
    protected final TransactionInflaters _transactionInflaters;

    public ThinTransactionsMessageInflater(final TransactionInflaters transactionInflaters) {
        _transactionInflaters = transactionInflaters;
    }

    @Override
    public ThinTransactionsMessage fromBytes(final byte[] bytes) {
        final ThinTransactionsMessage thinTransactionsMessage = new ThinTransactionsMessage(_transactionInflaters);
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.THIN_TRANSACTIONS);
        if (protocolMessageHeader == null) { return null; }

        final Sha256Hash blockHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        thinTransactionsMessage.setBlockHash(blockHash);

        final Integer transactionCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (transactionCount > BitcoinConstants.getMaxTransactionCountPerBlock()) { return null; }

        final TransactionInflater transactionInflater = _transactionInflaters.getTransactionInflater();
        final ImmutableListBuilder<Transaction> transactionListBuilder = new ImmutableListBuilder<Transaction>(transactionCount);
        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }

            transactionListBuilder.add(transaction);
        }
        thinTransactionsMessage.setTransactions(transactionListBuilder.build());

        if (byteArrayReader.didOverflow()) { return null; }

        return thinTransactionsMessage;
    }
}
