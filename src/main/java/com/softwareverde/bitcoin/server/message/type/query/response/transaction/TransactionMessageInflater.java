package com.softwareverde.bitcoin.server.message.type.query.response.transaction;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class TransactionMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public TransactionMessage fromBytes(final byte[] bytes) {
        final TransactionMessage transactionMessage = new TransactionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.TRANSACTION);
        if (protocolMessageHeader == null) { return null; }

        final TransactionInflater transactionInflater = new TransactionInflater();
        transactionMessage._transaction = transactionInflater.fromBytes(byteArrayReader);

        return transactionMessage;
    }
}
