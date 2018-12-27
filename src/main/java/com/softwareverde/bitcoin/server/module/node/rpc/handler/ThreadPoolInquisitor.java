package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.rpc.JsonRpcSocketServerHandler;
import com.softwareverde.concurrent.pool.MainThreadPool;

public class ThreadPoolInquisitor implements JsonRpcSocketServerHandler.ThreadPoolInquisitor {
    protected final MainThreadPool _threadPool;

    public ThreadPoolInquisitor(final MainThreadPool threadPool) {
        _threadPool = threadPool;
    }

    @Override
    public Integer getQueueCount() {
        return _threadPool.getQueueCount();
    }

    @Override
    public Integer getActiveThreadCount() {
        return _threadPool.getActiveThreadCount();
    }

    @Override
    public Integer getMaxThreadCount() {
        return _threadPool.getMaxThreadCount();
    }
}
