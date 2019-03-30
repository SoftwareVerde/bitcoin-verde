package com.softwareverde.bloomfilter;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;
import com.softwareverde.bitcoin.bloomfilter.UpdateBloomFilterMode;
import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class BloomFilterTests {

    protected static Sha256Hash createSha256(final int i) {
        return MutableSha256Hash.wrap(BitcoinUtil.sha256(ByteUtil.integerToBytes(i)));
    }

    @Test
    public void should_have_desired_false_positive_values_when_loaded_to_max_item_count() {
        // Setup
        final Double falsePositiveRate = 0.001D;
        final Long itemCount = 1024L;
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
        final Long itemCount = (1024L * 32L);
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(itemCount, 0.01D);

        // Action
        for (int i = 0; i < (itemCount * 1.5); ++i) {
            final ByteArray item = MutableByteArray.wrap(ByteUtil.longToBytes(i));
            bloomFilter.addItem(item);

            final Float expectedFalsePositiveRate = bloomFilter.getFalsePositiveRate((long) i);
            final Float calculatedFalsePositiveRate = bloomFilter.getFalsePositiveRate();

            // Assert
            Assert.assertEquals(expectedFalsePositiveRate, calculatedFalsePositiveRate, 0.005F);
        }
    }

    @Test
    public void should_create_deserialize_to_the_same_filter_as_bitcoin_core() {
        // Setup
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(3L, 0.01D, 0L);

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
    public void should_create_deserialize_to_the_same_filter_as_bitcoinj() {
        // Setup
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(3L, 0.01D, 2147483649L);
        bloomFilter.setUpdateMode(UpdateBloomFilterMode.P2PK_P2MS.getValue());

        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("99108AD8ED9BB6274D3980BAB5A85C048F0950C8")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("B5A2C786D9EF4658287CED5914B37A1B4AA32EEE")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("B9300670B4C5366E95B2699E8B18BC75E5F729C5")));

        // Action
        final ByteArray deflatedBloomFilter = bloomFilterDeflater.toBytes(bloomFilter);

        // Assert
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("03CE4299050000000100008002");
        TestUtil.assertEqual(expectedBytes, deflatedBloomFilter.getBytes());
    }

    @Test
    public void should_create_deserialize_to_the_same_filter_as_bitcoinj_2() {
        // Setup
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final MutableBloomFilter bloomFilter = new MutableBloomFilter(5L, 0.001D, 0L);
        bloomFilter.setUpdateMode(UpdateBloomFilterMode.P2PK_P2MS.getValue());

        // These values were acquired by manually inspecting the values added to the bitcoinj filter within its test...
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("045B81F0017E2091E2EDCD5EECF10D5BDD120A5514CB3EE65B8447EC18BFC4575C6D5BF415E54E03B1067934A0F0BA76B01C6B9AB227142EE1D543764B69D901E0")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("477ABBACD4113F2E6B100526222EEDD953C26A64")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("03CB219F69F1B49468BD563239A86667E74A06FCBA69AC50A08A5CBC42A5808E99")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("B12F2EF5AF078D5765212FFB45271CA9A4A1FD77")));
        bloomFilter.addItem(MutableByteArray.wrap(HexUtil.hexStringToByteArray("6408CC63471A969B603B56B022886EE5CC5CB2A2FA3E7102B5D34FB49E33E83C00000000")));

        // Action
        final ByteArray deflatedBloomFilter = bloomFilterDeflater.toBytes(bloomFilter);

        // Assert
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("082AE5EDC8E51D4A03080000000000000002");
        TestUtil.assertEqual(expectedBytes, deflatedBloomFilter.getBytes());
    }

    @Test
    public void should_inflate_and_deflate_bloom_filter() {
        // Setup
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final BloomFilterInflater bloomFilterInflater = new BloomFilterInflater();

        final Long itemCount = (32L * 1024L * 1024L / 256L);
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

        final Long itemCount = (32L * 1024L * 1024L / 256L);
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

    @Test
    public void static_match_all_bloom_filter_should_match_everything() {
        // Setup

        // Action
        for (int i = 0; i < 1024 * 1024; ++i) {
            final ByteArray object = MutableByteArray.wrap(ByteUtil.integerToBytes(i));

            // Assert
            final Boolean matchesObject = BloomFilter.MATCH_ALL.containsItem(object);

            Assert.assertTrue(matchesObject);
        }
    }

    @Test
    public void static_match_none_bloom_filter_should_match_nothing() {
        // Setup

        // Action
        for (int i = 0; i < 1024 * 1024; ++i) {
            final ByteArray object = MutableByteArray.wrap(ByteUtil.integerToBytes(i));

            // Assert
            final Boolean matchesObject = BloomFilter.MATCH_NONE.containsItem(object);

            Assert.assertFalse(matchesObject);
        }
    }

    @Test
    public void should_not_overflow_with_large_item_count() {
        // Setup
        final MutableBloomFilter mutableBloomFilter = new MutableBloomFilter(500_000_000L, 0.001D, 0L);

        // Action
        final Integer byteCount = mutableBloomFilter.getBytes().getByteCount();
        final Integer hashFunctionCount = mutableBloomFilter.getHashFunctionCount();
        final Float falsePositiveRate = mutableBloomFilter.getFalsePositiveRate(500_000_000L);

        // Assert
        Assert.assertEquals(898599222, byteCount.intValue());
        Assert.assertEquals(9, hashFunctionCount.intValue());
        Assert.assertEquals(0.001F, falsePositiveRate, 0.0001F);
    }

    // Test is disabled due to taking too long to run.
    // @Test
    public void should_not_match_items_not_within_bloom_filter() {
        // NOTE: This test identified an integer overflow bug in ByteArray regarding getting/setting bits in large buffers.
        // NOTE: This test also identified an integer overflow bug BloomFilter.

        // Setup
        final Double FALSE_POSITIVE_RATE = 0.001D;
        final MutableBloomFilter mutableBloomFilter = MutableBloomFilter.newInstance(500_000_000L, FALSE_POSITIVE_RATE, 0L);

        final Integer itemCount = 266770944;

        // Action
        for (int i = 0; i < itemCount; ++i) {
            final ByteArray object = createSha256(i);
            mutableBloomFilter.addItem(object);
            // Assert.assertTrue(mutableBloomFilter.containsItem(object));
            if (i % 1_770_944 == 0) System.out.println(i);
        }

        // Assert
        for (int i = 0; i < itemCount; ++i) {
            final ByteArray object = createSha256(i);
            Assert.assertTrue(mutableBloomFilter.containsItem(object));
        }

        int falsePositiveCount = 0;
        for (int i = 0; i < itemCount; ++i) {
            final ByteArray object = createSha256(i + itemCount);
            if (mutableBloomFilter.containsItem(object)) {
                falsePositiveCount += 1;
            }
            if (i % 1_770_944 == 0) System.out.println(i);
        }

        final float falsePositiveRate = ((float) falsePositiveCount) / itemCount;
        final int falsePositiveRateTimes1000 = (int) (falsePositiveRate * 1000);
        final int expectedFalsePositiveTimes1000 = (int) (FALSE_POSITIVE_RATE * 1000);
        Assert.assertTrue(falsePositiveRateTimes1000 <= expectedFalsePositiveTimes1000);
    }
}
