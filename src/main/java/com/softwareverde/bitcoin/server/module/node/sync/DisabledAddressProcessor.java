package com.softwareverde.bitcoin.server.module.node.sync;

public class DisabledAddressProcessor extends AddressProcessor {
    public DisabledAddressProcessor() {
        super(null, null);
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() { return false; }

    @Override
    protected void _onSleep() { }
}
