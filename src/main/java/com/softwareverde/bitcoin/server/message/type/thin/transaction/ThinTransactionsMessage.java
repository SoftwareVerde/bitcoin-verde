package com.softwareverde.bitcoin.server.message.type.thin.transaction;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class ThinTransactionsMessage extends BitcoinProtocolMessage {

    protected Sha256Hash _blockHash;
    protected List<Transaction> _transactions = new MutableList<Transaction>(0);

    public ThinTransactionsMessage() {
        super(MessageType.THIN_BLOCK);
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
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // Block Hash...
            byteArrayBuilder.appendBytes(_blockHash, Endian.LITTLE);
        }

        { // Transactions...
            final Integer transactionCount = _transactions.getSize();
            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount));
            for (final Transaction transaction : _transactions) {
                byteArrayBuilder.appendBytes(transactionDeflater.toBytes(transaction));
            }
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
