package com.softwareverde.bitcoin.server.module.node.database.address;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public class MutableSpendableTransactionOutput implements SpendableTransactionOutput {
    protected BlockId _blockId;
    protected TransactionId _transactionId;
    protected TransactionOutputId _transactionOutputId;
    protected Long _amount;
    protected TransactionInputId _spentByTransactionInputId;
    protected Boolean _isUnconfirmed;

    @Override
    public BlockId getBlockId() { return _blockId; }

    @Override
    public TransactionId getTransactionId() { return _transactionId; }

    @Override
    public TransactionOutputId getTransactionOutputId() { return _transactionOutputId; }

    @Override
    public Long getAmount() { return _amount; }

    @Override
    public TransactionInputId getSpentByTransactionInputId() { return _spentByTransactionInputId; }

    @Override
    public Boolean wasSpent() { return (_spentByTransactionInputId != null); }

    @Override
    public Boolean isMined() { return (_blockId != null); }

    @Override
    public Boolean isUnconfirmed() { return _isUnconfirmed; }

    public void setBlockId(final BlockId blockId) {
        _blockId = blockId;
    }

    public void setTransactionId(final TransactionId transactionId) {
        _transactionId = transactionId;
    }

    public void setTransactionOutputId(final TransactionOutputId transactionOutputId) {
        _transactionOutputId = transactionOutputId;
    }

    public void setAmount(final Long amount) {
        _amount = amount;
    }

    public void setSpentByTransactionInputId(final TransactionInputId spentByTransactionInputId) {
        _spentByTransactionInputId = spentByTransactionInputId;
    }

    public void setIsUnconfirmed(final Boolean isUnconfirmed) {
        _isUnconfirmed = isUnconfirmed;
    }
}