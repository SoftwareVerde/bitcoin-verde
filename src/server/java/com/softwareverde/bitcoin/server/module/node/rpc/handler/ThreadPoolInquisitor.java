package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.concurrent.pool.cached.CachedThreadPool;

public class ThreadPoolInquisitor implements NodeRpcHandler.ThreadPoolInquisitor {
    protected final CachedThreadPool _threadPool;

    public ThreadPoolInquisitor(final CachedThreadPool threadPool) {
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
