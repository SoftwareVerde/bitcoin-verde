package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

class UnspentTransactionOutput extends TransactionOutputIdentifier {
    protected final Long _blockHeight;

    public UnspentTransactionOutput(final Sha256Hash transactionHash, final Integer index, final Long blockHeight) {
        super(transactionHash, index);
        _blockHeight = blockHeight;
    }

    public Long getBlockHeight() {
        return _blockHeight;
    }
}
