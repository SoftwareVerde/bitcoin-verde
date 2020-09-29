package com.softwareverde.bitcoin.server.module.node.sync;

public class DisabledBlockchainIndexer extends BlockchainIndexer {
    public DisabledBlockchainIndexer() {
        super(null, 0);
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() { return false; }

    @Override
    protected void _onSleep() { }
}
