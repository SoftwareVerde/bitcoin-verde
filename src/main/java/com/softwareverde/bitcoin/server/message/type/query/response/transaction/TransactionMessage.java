package com.softwareverde.bitcoin.server.message.type.query.response.transaction;

import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.constable.bytearray.ByteArray;

public class TransactionMessage extends BitcoinProtocolMessage {

    protected final TransactionInflaters _transactionInflaters;
    protected Transaction _transaction;

    public TransactionMessage(final TransactionInflaters transactionInflaters) {
        super(MessageType.TRANSACTION);
        _transactionInflaters = transactionInflaters;
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public void setTransaction(final Transaction transaction) {
        _transaction = transaction;
    }

    @Override
    protected ByteArray _getPayload() {
        final TransactionDeflater transactionDeflater = _transactionInflaters.getTransactionDeflater();
        return transactionDeflater.toBytes(_transaction);
    }
}
