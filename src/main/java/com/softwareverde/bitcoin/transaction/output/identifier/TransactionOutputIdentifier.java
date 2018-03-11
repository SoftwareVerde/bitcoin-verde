package com.softwareverde.bitcoin.transaction.output.identifier;

import com.softwareverde.bitcoin.chain.BlockChainId;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.Const;

public class TransactionOutputIdentifier implements Const {
    protected final BlockChainId _blockChainId;
    protected final Hash _transactionHash;
    protected final Integer _outputIndex;

    public TransactionOutputIdentifier(final BlockChainId blockChainId, final Hash transactionHash, final Integer outputIndex) {
        _blockChainId = blockChainId;
        _transactionHash = transactionHash.asConst();
        _outputIndex = outputIndex;
    }

    public Hash getTransactionHash() {
        return _transactionHash;
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    public BlockChainId getBlockChainId() {
        return _blockChainId;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionOutputIdentifier)) { return false; }
        final TransactionOutputIdentifier transactionOutputIdentifier = (TransactionOutputIdentifier) object;

        if (_blockChainId != null) {
            if (! _blockChainId.equals(transactionOutputIdentifier._blockChainId)) { return false; }
        }
        else {
            if (transactionOutputIdentifier._blockChainId != null) { return false; }
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
