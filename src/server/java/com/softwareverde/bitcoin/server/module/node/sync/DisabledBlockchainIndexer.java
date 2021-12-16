package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;

public class DisabledBlockchainIndexer extends BlockchainIndexer {
    public DisabledBlockchainIndexer() {
        super(null, 0);
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _execute() { return false; }

    @Override
    protected void _onSleep() { }

    @Override
    public TransactionId indexUtxosFromUtxoCommitmentImport(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final List<TransactionOutput> transactionOutputs) {
        return null;
    }
}
