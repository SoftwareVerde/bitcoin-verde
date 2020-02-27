package com.softwareverde.bitcoin.server.module.node.sync;

public class DisabledTransactionOutputIndexer extends TransactionOutputIndexer {
    public DisabledTransactionOutputIndexer() {
        super(null);
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() { return false; }

    @Override
    protected void _onSleep() { }
}
