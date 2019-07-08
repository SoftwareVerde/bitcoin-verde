package com.softwareverde.bitcoin.server.message.type.query.response.transaction;

import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class TransactionMessageInflater extends BitcoinProtocolMessageInflater {
    protected final TransactionInflater _transactionInflater;

    public TransactionMessageInflater(final TransactionInflaters transactionInflaters) {
        _transactionInflater = transactionInflaters.getTransactionInflater();
    }

    @Override
    public TransactionMessage fromBytes(final byte[] bytes) {
        final TransactionMessage transactionMessage = new TransactionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.TRANSACTION);
        if (protocolMessageHeader == null) { return null; }

        transactionMessage._transaction = _transactionInflater.fromBytes(byteArrayReader);

        return transactionMessage;
    }
}
