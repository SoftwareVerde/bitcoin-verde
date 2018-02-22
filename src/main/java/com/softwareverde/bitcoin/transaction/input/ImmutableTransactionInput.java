package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.ByteUtil;

public class ImmutableTransactionInput implements TransactionInput {
    private final TransactionInput _transactionInput;

    public ImmutableTransactionInput(final TransactionInput transactionInput) {
        if (transactionInput instanceof ImmutableTransactionInput) {
            _transactionInput = transactionInput;
            return;
        }

        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
        mutableTransactionInput.setUnlockingScript(ByteUtil.copyBytes(transactionInput.getUnlockingScript()));
        mutableTransactionInput.setPreviousTransactionOutput(new ImmutableHash(transactionInput.getPreviousTransactionOutput()));
        mutableTransactionInput.setPreviousTransactionOutputIndex(transactionInput.getPreviousTransactionOutputIndex());
        mutableTransactionInput.setSequenceNumber(transactionInput.getSequenceNumber());
        _transactionInput = mutableTransactionInput;
    }

    @Override
    public Hash getPreviousTransactionOutput() {
        return new ImmutableHash(_transactionInput.getPreviousTransactionOutput());
    }

    @Override
    public Integer getPreviousTransactionOutputIndex() {
        return _transactionInput.getPreviousTransactionOutputIndex();
    }

    @Override
    public byte[] getUnlockingScript() {
        return ByteUtil.copyBytes(_transactionInput.getUnlockingScript());
    }

    @Override
    public Long getSequenceNumber() {
        return _transactionInput.getSequenceNumber();
    }

    @Override
    public Integer getByteCount() {
        return _transactionInput.getByteCount();
    }

    @Override
    public byte[] getBytes() {
        // return ByteUtil.copyBytes(_transactionInput.getBytes());
        return _transactionInput.getBytes(); // NOTE: This is already a copied byte[]...
    }
}
