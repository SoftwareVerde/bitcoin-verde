package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;

public interface BloomFilterInflaters extends Inflater {
    BloomFilterInflater getBloomFilterInflater();
    BloomFilterDeflater getBloomFilterDeflater();
}
