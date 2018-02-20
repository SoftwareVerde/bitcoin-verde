package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

import java.util.ArrayList;
import java.util.List;

public class MutableBlock extends MutableBlockHeader implements Block {
    protected final List<Transaction> _transactions = new ArrayList<Transaction>();

    public MutableBlock() { }
    public MutableBlock(final BlockHeader blockHeader) {
        _version = blockHeader.getVersion();
        _previousBlockHash = blockHeader.getPreviousBlockHash();
        _merkleRoot = blockHeader.getMerkleRoot();
        _timestamp = blockHeader.getTimestamp();
        _difficulty = blockHeader.getDifficulty();
        _nonce = blockHeader.getNonce();
    }

    public void addTransaction(final Transaction transaction) {
        _transactions.add(transaction);
    }

    public void clearTransactions() {
        _transactions.clear();
    }

    public List<Transaction> getTransactions() {
        final List<Transaction> transactions = new ArrayList<Transaction>(_transactions.size());
        for (final Transaction transaction : _transactions) {
            transactions.add(transaction.copy());
        }
        return transactions;
    }

    @Override
    protected Integer _getTransactionCount() {
        return _transactions.size();
    }

    @Override
    public byte[] getBytes() {
        final ByteData byteData = _createByteData();
        final byte[] headerBytes = byteData.serialize();
        final int transactionCount = _transactions.size();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(headerBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount), Endian.LITTLE);

        for (int i=0; i<transactionCount; ++i) {
            final Transaction transaction = _transactions.get(i);
            final byte[] transactionBytes = transaction.getBytes();
            byteArrayBuilder.appendBytes(transactionBytes, Endian.BIG);
        }

        return byteArrayBuilder.build();
    }
}
