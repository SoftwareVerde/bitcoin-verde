package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface MedianBlockTimeSet {
    MedianBlockTime getMedianBlockTime(Sha256Hash blockHash);
}
