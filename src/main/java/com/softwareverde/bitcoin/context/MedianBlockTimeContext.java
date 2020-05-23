package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.chain.time.*;

public interface MedianBlockTimeContext {
    MedianBlockTime getMedianBlockTime(Long blockHeight);
}
