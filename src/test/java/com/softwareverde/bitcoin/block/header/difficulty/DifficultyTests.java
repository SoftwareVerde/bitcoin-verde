package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Assert;
import org.junit.Test;

public class DifficultyTests {
    @Test
    public void should_return_bytes_from_exponent_and_significand_0() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("FFFF"), (0x1D - 0x03));
        final byte[] expectedBytes = BitcoinUtil.hexStringToByteArray("00000000FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_return_bytes_from_exponent_and_significand_1() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        final byte[] expectedBytes = BitcoinUtil.hexStringToByteArray("00000000FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_return_bytes_from_exponent_and_significand_2() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("00A429"), (0x1A - 0x03));
        final byte[] expectedBytes = BitcoinUtil.hexStringToByteArray("00000000000000A4290000000000000000000000000000000000000000000000");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_not_be_satisfied_by_larger_hash() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = BitcoinUtil.hexStringToByteArray( "00000001FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(sha256Hash);

        // Assert
        Assert.assertFalse(isSatisfied);
    }

    @Test
    public void should_be_satisfied_by_equal_hash() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = BitcoinUtil.hexStringToByteArray( "00000000FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(sha256Hash);

        // Assert
        Assert.assertTrue(isSatisfied);
    }

    @Test
    public void should_be_satisfied_by_smaller_hash_0() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = BitcoinUtil.hexStringToByteArray( "00000000FFFE0000000000000000000000000000000000000000000000000000");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(sha256Hash);

        // Assert
        Assert.assertTrue(isSatisfied);
    }

    @Test
    public void should_be_satisfied_by_smaller_hash_1() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = BitcoinUtil.hexStringToByteArray( "000000000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(sha256Hash);

        // Assert
        Assert.assertTrue(isSatisfied);
    }

    @Test
    public void should_encode_base_difficulty() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        final byte[] expectedEncoding = BitcoinUtil.hexStringToByteArray("1D00FFFF");

        // Action
        final byte[] encodedBytes = difficulty.encode();

        // Assert
        TestUtil.assertEqual(expectedEncoding, encodedBytes);
    }

    @Test
    public void should_return_bytes_from_small_exponent_and_significand_0() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("01"), 0x00);
        final byte[] expectedBytes = BitcoinUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000001");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_return_bytes_from_small_exponent_and_significand_1() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("FF"), 0x01);
        final byte[] expectedBytes = BitcoinUtil.hexStringToByteArray("000000000000000000000000000000000000000000000000000000000000FF00");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_encode_and_decode_bytes_from_small_exponent_and_significand() {
        // Setup
        final Difficulty difficulty = new Difficulty(BitcoinUtil.hexStringToByteArray("FF"), 0x00);
        final byte[] expectedEncodedBytes = BitcoinUtil.hexStringToByteArray("030000FF");
        final byte[] expectedRawBytes = BitcoinUtil.hexStringToByteArray("00000000000000000000000000000000000000000000000000000000000000FF");

        // Action
        final byte[] encodedBytes = difficulty.encode();
        final byte[] rawBytes0 = difficulty.getBytes();
        final byte[] rawBytes1 = Difficulty.decode(encodedBytes).getBytes();

        // Assert
        TestUtil.assertEqual(expectedEncodedBytes, encodedBytes);
        TestUtil.assertEqual(expectedRawBytes, rawBytes0);
        TestUtil.assertEqual(expectedRawBytes, rawBytes1);
    }

    @Test
    public void should_encode_and_decode_base_difficulty() {
        // Setup
        final byte[] encodedBaseDifficulty = BitcoinUtil.hexStringToByteArray("1D00FFFF");
        final byte[] expectedRawBytes = BitcoinUtil.hexStringToByteArray("00000000FFFF0000000000000000000000000000000000000000000000000000");

        final Difficulty difficulty0, difficulty1;

        // Action
        difficulty0 = Difficulty.decode(encodedBaseDifficulty);
        final byte[] encodedBytes0 = difficulty0.encode();
        difficulty1 = Difficulty.decode(encodedBytes0);

        final byte[] rawBytes0 = difficulty0.getBytes();
        final byte[] rawBytes1 = difficulty1.getBytes();

        // Assert
        TestUtil.assertEqual(expectedRawBytes, rawBytes0);
        TestUtil.assertEqual(expectedRawBytes, rawBytes1);
    }
}
