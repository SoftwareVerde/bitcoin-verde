package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public interface MedianHeadBlockTimeContext {
    MedianBlockTime getHeadMedianBlockTime();
}
