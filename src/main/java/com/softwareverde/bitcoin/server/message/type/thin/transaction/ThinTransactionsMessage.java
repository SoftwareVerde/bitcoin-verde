package com.softwareverde.bitcoin.server.message.type.thin.transaction;

import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class ThinTransactionsMessage extends BitcoinProtocolMessage {
    protected final TransactionInflaters _transactionInflaters;

    protected Sha256Hash _blockHash;
    protected List<Transaction> _transactions = new MutableList<Transaction>(0);

    public ThinTransactionsMessage(final TransactionInflaters transactionInflaters) {
        super(MessageType.THIN_TRANSACTIONS);
        _transactionInflaters = transactionInflaters;
    }

    public Sha256Hash getBlockHash() {
        return _blockHash;
    }

    public List<Transaction> getTransactions() {
        return _transactions;
    }

    public void setBlockHash(final Sha256Hash blockHash) {
        _blockHash = blockHash;
    }

    public void setTransactions(final List<Transaction> transactions) {
        _transactions = transactions.asConst();
    }

    @Override
    protected ByteArray _getPayload() {
        final TransactionDeflater transactionDeflater = _transactionInflaters.getTransactionDeflater();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // Block Hash...
            byteArrayBuilder.appendBytes(_blockHash, Endian.LITTLE);
        }

        { // Transactions...
            final int transactionCount = _transactions.getCount();
            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount));
            for (final Transaction transaction : _transactions) {
                byteArrayBuilder.appendBytes(transactionDeflater.toBytes(transaction));
            }
        }

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final TransactionDeflater transactionDeflater = _transactionInflaters.getTransactionDeflater();

        int totalTransactionByteCount = 0;
        for (final Transaction transaction : _transactions) {
            totalTransactionByteCount += transactionDeflater.getByteCount(transaction);
        }

        final int transactionCount = _transactions.getCount();
        final byte[] transactionCountBytes = ByteUtil.variableLengthIntegerToBytes(transactionCount);
        return (Sha256Hash.BYTE_COUNT + transactionCountBytes.length + totalTransactionByteCount);
    }
}
