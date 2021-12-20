package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.stratum.callback.BlockFoundCallback;
import com.softwareverde.bitcoin.server.module.stratum.callback.WorkerShareCallback;

public interface StratumServer {
    Long getCurrentBlockStartTimeInSeconds();
    Long getStartTimeInSeconds();
    Long getShareCount();
    Long getShareDifficulty();
    Long getBlockHeight();
    Block getPrototypeBlock();

    void setShareDifficulty(Long baseShareDifficulty);
    void setWorkerShareCallback(WorkerShareCallback workerShareCallback);
    void setBlockFoundCallback(BlockFoundCallback blockFoundCallback);

    void start();
    void stop();
}
