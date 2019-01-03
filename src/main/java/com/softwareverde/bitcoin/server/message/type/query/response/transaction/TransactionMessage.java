package com.softwareverde.bitcoin.server.message.type.query.response.transaction;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.constable.bytearray.ByteArray;

public class TransactionMessage extends BitcoinProtocolMessage {

    protected Transaction _transaction;

    public TransactionMessage() {
        super(MessageType.TRANSACTION);
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public void setTransaction(final Transaction transaction) {
        _transaction = transaction;
    }

    @Override
    protected ByteArray _getPayload() {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        return transactionDeflater.toBytes(_transaction);
    }
}
