package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public interface MedianBlockTimeContext {
    MedianBlockTime getMedianBlockTime(Long blockHeight);
}
