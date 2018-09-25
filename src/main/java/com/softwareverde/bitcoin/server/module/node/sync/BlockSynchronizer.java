package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;

public interface BlockSynchronizer {
    void start();
    void stop();

    JsonRpcSocketServerHandler.StatisticsContainer getStatisticsContainer();
}
