package com.softwareverde.bloomfilter;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class BloomFilterTests {

    @Test
    public void should_have_desired_false_positive_values_when_loaded_to_max_item_count() {
        // Setup
        final Double falsePositiveRate = 0.001D;
        final Integer itemCount = 1024;
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(itemCount, falsePositiveRate);

        for (int i = 0; i < itemCount; ++i) {
            final ByteArray item = MutableByteArray.wrap(ByteUtil.longToBytes((long) i));
            bloomFilter.addItem(item);
        }

        final int iterationCount = 1000000;
        int falsePositiveCount = 0;
        for (int i = 0; i < iterationCount; ++i) {
            final ByteArray item = MutableByteArray.wrap(ByteUtil.longToBytes((long) (i + itemCount)));
            if (bloomFilter.containsItem(item)) {
                falsePositiveCount += 1;
            }
        }

        final Float actualFalsePositiveRate = ( ((float) falsePositiveCount) / iterationCount );
        System.out.println("Bloom Filter False Positive Rate: " + falsePositiveCount + " / " + iterationCount + " ("+ (actualFalsePositiveRate * 100F) +"%)");
        Assert.assertEquals(falsePositiveRate, actualFalsePositiveRate, 0.005);
    }

    @Test
    public void should_calculate_false_positive_rate() {
        // Setup
        final Integer itemCount = (1024 * 32);
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(itemCount, 0.01D);

        // Action
        for (int i = 0; i < (itemCount * 1.5); ++i) {
            final ByteArray item = MutableByteArray.wrap(ByteUtil.longToBytes(i));
            bloomFilter.addItem(item);

            final Float expectedFalsePositiveRate = bloomFilter.getFalsePositiveRate(i);
            final Float calculatedFalsePositiveRate = bloomFilter.getFalsePositiveRate();

            // Assert
            Assert.assertEquals(expectedFalsePositiveRate, calculatedFalsePositiveRate, 0.005F);
        }
    }


    @Test
    public void should_create_deserialize_to_the_same_filter_as_bitcoin_core() {
        // Setup
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(3, 0.01D, 0L);

        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("99108AD8ED9BB6274D3980BAB5A85C048F0950C8")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("B5A2C786D9EF4658287CED5914B37A1B4AA32EEE")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("B9300670B4C5366E95B2699E8B18BC75E5F729C5")));

        // Action
        final ByteArray deflatedBloomFilter = bloomFilterDeflater.toBytes(bloomFilter);

        // Assert
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("03614E9B050000000000000001");
        TestUtil.assertEqual(expectedBytes, deflatedBloomFilter.getBytes());
    }

    @Test
    public void should_inflate_and_deflate_bloom_filter() {
        // Setup
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final BloomFilterInflater bloomFilterInflater = new BloomFilterInflater();

        final Integer itemCount = (32 * 1024 * 1024 / 256);
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(itemCount, 0.01D);

        for (int i = 0; i < itemCount; ++i) {
            final ByteArray item = MutableByteArray.wrap(ByteUtil.longToBytes(i));
            bloomFilter.addItem(item);
        }

        // Action
        final ByteArray deflatedBloomFilter = bloomFilterDeflater.toBytes(bloomFilter);
        final BloomFilter inflatedBloomFilter = bloomFilterInflater.fromBytes(deflatedBloomFilter);

        // Assert
        Assert.assertEquals(bloomFilter, inflatedBloomFilter);
    }

    @Test
    public void should_inflate_and_deflate_bloom_filter_with_unsigned_int_nonce() {
        // Setup
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final BloomFilterInflater bloomFilterInflater = new BloomFilterInflater();

        final Integer itemCount = (32 * 1024 * 1024 / 256);
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(itemCount, 0.01D, Long.MAX_VALUE);

        for (int i = 0; i < itemCount; ++i) {
            final ByteArray item = MutableByteArray.wrap(ByteUtil.longToBytes(i));
            bloomFilter.addItem(item);
        }

        // Action
        final ByteArray deflatedBloomFilter = bloomFilterDeflater.toBytes(bloomFilter);
        final BloomFilter inflatedBloomFilter = bloomFilterInflater.fromBytes(deflatedBloomFilter);

        // Assert
        Assert.assertEquals(bloomFilter, inflatedBloomFilter);
    }
}
