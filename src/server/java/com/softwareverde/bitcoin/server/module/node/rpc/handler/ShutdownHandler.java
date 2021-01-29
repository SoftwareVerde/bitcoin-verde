package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;

public class ShutdownHandler implements NodeRpcHandler.ShutdownHandler {
    protected final Thread _mainThread;
    protected final SynchronizationStatusHandler _synchronizationStatusHandler;

    public ShutdownHandler(final Thread mainThread, final SynchronizationStatusHandler synchronizationStatusHandler) {
        _mainThread = mainThread;
        _synchronizationStatusHandler = synchronizationStatusHandler;
    }

    @Override
    public Boolean shutdown() {
        _synchronizationStatusHandler.setState(State.SHUTTING_DOWN);
        _mainThread.interrupt();
        return true;
    }
}
