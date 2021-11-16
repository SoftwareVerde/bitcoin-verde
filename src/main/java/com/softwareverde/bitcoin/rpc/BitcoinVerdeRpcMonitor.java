package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.rpc.monitor.RpcMonitor;

public class BitcoinVerdeRpcMonitor extends RpcMonitor<NodeJsonRpcConnection> {

    @Override
    protected void _cancelRequest() {
        _connection.close();
    }

    public BitcoinVerdeRpcMonitor() { }

    @Override
    protected void beforeRequestStart(final NodeJsonRpcConnection nodeJsonRpcConnection) {
        super.beforeRequestStart(nodeJsonRpcConnection);
    }

    @Override
    protected void afterRequestEnd() {
        super.afterRequestEnd();
    }
}
