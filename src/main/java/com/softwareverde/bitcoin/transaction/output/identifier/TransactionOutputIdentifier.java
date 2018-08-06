package com.softwareverde.bitcoin.transaction.output.identifier;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.Const;

public class TransactionOutputIdentifier implements Const {
    protected final BlockChainSegmentId _blockChainSegmentId;
    protected final Sha256Hash _transactionHash;
    protected final Integer _outputIndex;

    public TransactionOutputIdentifier(final BlockChainSegmentId blockChainSegmentId, final Sha256Hash transactionHash, final Integer outputIndex) {
        _blockChainSegmentId = blockChainSegmentId;
        _transactionHash = transactionHash.asConst();
        _outputIndex = outputIndex;
    }

    public Sha256Hash getTransactionHash() {
        return _transactionHash;
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    public BlockChainSegmentId getBlockChainSegmentId() {
        return _blockChainSegmentId;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionOutputIdentifier)) { return false; }
        final TransactionOutputIdentifier transactionOutputIdentifier = (TransactionOutputIdentifier) object;

        if (_blockChainSegmentId != null) {
            if (! _blockChainSegmentId.equals(transactionOutputIdentifier._blockChainSegmentId)) { return false; }
        }
        else {
            if (transactionOutputIdentifier._blockChainSegmentId != null) { return false; }
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
