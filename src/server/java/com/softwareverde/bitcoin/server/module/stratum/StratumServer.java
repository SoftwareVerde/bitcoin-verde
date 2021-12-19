package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.block.Block;

public interface StratumServer {
    Long getCurrentBlockStartTimeInSeconds();
    Long getStartTimeInSeconds();
    Long getShareCount();
    Long getShareDifficulty();
    Long getBlockHeight();
    Block getPrototypeBlock();
    void setWorkerShareCallback(final WorkerShareCallback workerShareCallback);

    void start();
    void stop();
}
