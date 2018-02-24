package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;

public class ImmutableTransactionInput implements TransactionInput {
    private final TransactionInput _transactionInput;

    public ImmutableTransactionInput(final TransactionInput transactionInput) {
        if (transactionInput instanceof ImmutableTransactionInput) {
            _transactionInput = transactionInput;
            return;
        }

        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
        mutableTransactionInput.setUnlockingScript(new ImmutableScript(transactionInput.getUnlockingScript()));
        mutableTransactionInput.setPreviousTransactionOutput(new ImmutableHash(transactionInput.getOutputTransactionHash()));
        mutableTransactionInput.setPreviousTransactionOutputIndex(transactionInput.getOutputTransactionIndex());
        mutableTransactionInput.setSequenceNumber(transactionInput.getSequenceNumber());
        _transactionInput = mutableTransactionInput;
    }

    @Override
    public Hash getOutputTransactionHash() {
        return new ImmutableHash(_transactionInput.getOutputTransactionHash());
    }

    @Override
    public Integer getOutputTransactionIndex() {
        return _transactionInput.getOutputTransactionIndex();
    }

    @Override
    public Script getUnlockingScript() {
        return _transactionInput.getUnlockingScript();
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
