package com.softwareverde.bitcoin.server.message.type.query.response.transaction;

import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class TransactionMessageInflater extends BitcoinProtocolMessageInflater {
    protected final TransactionInflaters _transactionInflaters;

    public TransactionMessageInflater(final TransactionInflaters transactionInflaters) {
        _transactionInflaters = transactionInflaters;
    }

    @Override
    public TransactionMessage fromBytes(final byte[] bytes) {
        final TransactionMessage transactionMessage = new TransactionMessage(_transactionInflaters);
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.TRANSACTION);
        if (protocolMessageHeader == null) { return null; }

        final TransactionInflater transactionInflater = _transactionInflaters.getTransactionInflater();
        transactionMessage._transaction = transactionInflater.fromBytes(byteArrayReader);

        return transactionMessage;
    }
}
