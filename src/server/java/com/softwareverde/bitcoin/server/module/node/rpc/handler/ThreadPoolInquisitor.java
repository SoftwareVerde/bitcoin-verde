package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;

public class ThreadPoolInquisitor implements NodeRpcHandler.ThreadPoolInquisitor {
    protected final CachedThreadPool _threadPool;

    public ThreadPoolInquisitor(final CachedThreadPool threadPool) {
        _threadPool = threadPool;
    }

    @Override
    public Integer getQueueCount() {
        return _threadPool.getQueuedTaskCount();
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
