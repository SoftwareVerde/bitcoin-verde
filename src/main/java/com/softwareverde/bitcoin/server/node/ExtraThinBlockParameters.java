package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;

public class ExtraThinBlockParameters {
    public final BlockHeader blockHeader;
    public final List<ByteArray> transactionHashes;
    public final List<Transaction> transactions;

    public ExtraThinBlockParameters(final BlockHeader blockHeader, final List<ByteArray> transactionHashes, final List<Transaction> transactions) {
        this.blockHeader = blockHeader;
        this.transactionHashes = transactionHashes;
        this.transactions = transactions;
    }
}
