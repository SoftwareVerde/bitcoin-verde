package com.softwareverde.bitcoin.server.module.node.rpc.core;

import com.softwareverde.http.HttpRequest;

public class BitcoinCoreRpcMonitor extends RpcMonitor<HttpRequest> {

    @Override
    protected void _cancelRequest() {
        _connection.cancel();
    }

    public BitcoinCoreRpcMonitor() { }
}
