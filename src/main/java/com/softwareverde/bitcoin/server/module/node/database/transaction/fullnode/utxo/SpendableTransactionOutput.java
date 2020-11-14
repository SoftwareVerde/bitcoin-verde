package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

class SpendableTransactionOutput extends TransactionOutputIdentifier {
    protected final Long _blockHeight;
    protected final Boolean _isSpent;

    public SpendableTransactionOutput(final Sha256Hash transactionHash, final Integer index, final Boolean isSpent, final Long blockHeight) {
        super(transactionHash, index);
        _blockHeight = blockHeight;
        _isSpent = isSpent;
    }

    public Long getBlockHeight() {
        return _blockHeight;
    }

    public Boolean isSpent() {
        return _isSpent;
    }
}
