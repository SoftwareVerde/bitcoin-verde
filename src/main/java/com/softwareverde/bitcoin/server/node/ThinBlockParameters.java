package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class ThinBlockParameters {
    public final BlockHeader blockHeader;
    public final List<Sha256Hash> transactionHashes;
    public final List<Transaction> transactions;

    public ThinBlockParameters(final BlockHeader blockHeader, final List<Sha256Hash> transactionHashes, final List<Transaction> transactions) {
        this.blockHeader = blockHeader;
        this.transactionHashes = transactionHashes;
        this.transactions = transactions;
    }
}
