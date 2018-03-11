package com.softwareverde.bitcoin.transaction.output.identifier;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.Const;

public class TransactionOutputIdentifier implements Const {
    protected final BlockId _blockId;
    protected final Hash _transactionHash;
    protected final Integer _outputIndex;

    public TransactionOutputIdentifier(final BlockId blockId, final Hash transactionHash, final Integer outputIndex) {
        _blockId = blockId;
        _transactionHash = transactionHash.asConst();
        _outputIndex = outputIndex;
    }

    public Hash getTransactionHash() {
        return _transactionHash;
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    public BlockId getBlockId() {
        return _blockId;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionOutputIdentifier)) { return false; }
        final TransactionOutputIdentifier transactionOutputIdentifier = (TransactionOutputIdentifier) object;

        if (_blockId != null) {
            if (! _blockId.equals(transactionOutputIdentifier._blockId)) { return false; }
        }
        else {
            if (transactionOutputIdentifier._blockId != null) { return false; }
        }

        if (_outputIndex != null) {
            if (! _outputIndex.equals(transactionOutputIdentifier._outputIndex)) { return false; }
        }
        else {
            if (transactionOutputIdentifier._outputIndex != null) { return false; }
        }

        if (! _transactionHash.equals(transactionOutputIdentifier._transactionHash)) { return false; }

        return true;
    }
}
