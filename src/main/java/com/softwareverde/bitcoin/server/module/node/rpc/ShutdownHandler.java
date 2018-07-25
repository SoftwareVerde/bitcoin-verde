package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;

public class ShutdownHandler implements JsonRpcSocketServerHandler.ShutdownHandler {
    protected final Thread _mainThread;

    public ShutdownHandler(final Thread mainThread) {
        _mainThread = mainThread;
    }

    @Override
    public Boolean shutdown() {
        _mainThread.interrupt();
        return true;
    }
}
