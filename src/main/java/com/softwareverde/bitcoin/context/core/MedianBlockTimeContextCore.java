package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.server.module.node.Blockchain;

public class MedianBlockTimeContextCore implements MedianBlockTimeContext {
    protected Blockchain _blockchain;
    public MedianBlockTimeContextCore(final Blockchain blockchain) {
        _blockchain = blockchain;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        return null;
    }
}
