package com.softwareverde.bitcoin.stratum;

import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.stratum.callback.BlockFoundCallback;
import com.softwareverde.bitcoin.stratum.callback.WorkerShareCallback;

public interface StratumServer {
    Long getCurrentBlockStartTimeInSeconds();
    Long getStartTimeInSeconds();
    Long getShareCount();
    Long getShareDifficulty();
    Long getBlockHeight();
    Block getPrototypeBlock();

    void setCoinbaseAddress(ParsedAddress address);
    void setShareDifficulty(Long baseShareDifficulty);
    void setWorkerShareCallback(WorkerShareCallback workerShareCallback);
    void setBlockFoundCallback(BlockFoundCallback blockFoundCallback);

    /**
     * When enabled, the ShareDifficulty is used as a multiplier, not divisor.
     *  This function may be used to accept shares less than the base difficulty.
     */
    void invertDifficulty(Boolean shouldInvertDifficulty);

    void start();
    void stop();
}
