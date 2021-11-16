package com.softwareverde.bitcoin.rpc.core;

import com.softwareverde.bitcoin.rpc.monitor.RpcMonitor;
import com.softwareverde.http.HttpRequest;

public class BitcoinCoreRpcMonitor extends RpcMonitor<HttpRequest> {

    @Override
    protected void _cancelRequest() {
        _connection.cancel();
    }

    public BitcoinCoreRpcMonitor() { }

    @Override
    protected void beforeRequestStart(final HttpRequest connection) {
        super.beforeRequestStart(connection);
    }

    @Override
    protected void afterRequestEnd() {
        super.afterRequestEnd();
    }
}
