package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.Util;

public class MerkleBlockParameters {
    protected final MerkleBlock _merkleBlock;
    protected final MutableList<Transaction> _transactions = new MutableArrayList<>();

    public MerkleBlock getMerkleBlock() {
        return _merkleBlock;
    }

    public List<Transaction> getTransactions() {
        return _transactions;
    }

    public MerkleBlockParameters(final MerkleBlock merkleBlock) {
        _merkleBlock = merkleBlock.asConst();
    }

    protected Boolean hasAllTransactions() {
        return Util.areEqual(_merkleBlock.getTransactionCount(), _transactions.getCount());
    }

    protected void addTransaction(final Transaction transaction) {
        _transactions.add(transaction.asConst());
    }

    public Integer getByteCount() {
        int byteCount = 0;
        byteCount += _merkleBlock.getByteCount();
        for (final Transaction transaction : _transactions) {
            byteCount += transaction.getByteCount();
        }
        return byteCount;
    }
}
