package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;

public class DifficultyTests {
    @Test
    public void should_return_bytes_from_exponent_and_significand_0() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("FFFF"), (0x1D - 0x03));
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("00000000FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_return_bytes_from_exponent_and_significand_1() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("00000000FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_return_bytes_from_exponent_and_significand_2() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00A429"), (0x1A - 0x03));
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("00000000000000A4290000000000000000000000000000000000000000000000");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_not_be_satisfied_by_larger_hash() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(HexUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = HexUtil.hexStringToByteArray( "00000001FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(MutableSha256Hash.wrap(sha256Hash));

        // Assert
        Assert.assertFalse(isSatisfied);
    }

    @Test
    public void should_be_satisfied_by_equal_hash() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(HexUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = HexUtil.hexStringToByteArray( "00000000FFFF0000000000000000000000000000000000000000000000000000");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(MutableSha256Hash.wrap(sha256Hash));

        // Assert
        Assert.assertTrue(isSatisfied);
    }

    @Test
    public void should_be_satisfied_by_smaller_hash_0() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(HexUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = HexUtil.hexStringToByteArray( "00000000FFFE0000000000000000000000000000000000000000000000000000");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(MutableSha256Hash.wrap(sha256Hash));

        // Assert
        Assert.assertTrue(isSatisfied);
    }

    @Test
    public void should_be_satisfied_by_smaller_hash_1() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        TestUtil.assertEqual(HexUtil.hexStringToByteArray(      "00000000FFFF0000000000000000000000000000000000000000000000000000"), difficulty.getBytes()); // Ensure our decoding is sane...
        final byte[] sha256Hash = HexUtil.hexStringToByteArray( "000000000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

        // Action
        final Boolean isSatisfied = difficulty.isSatisfiedBy(MutableSha256Hash.wrap(sha256Hash));

        // Assert
        Assert.assertTrue(isSatisfied);
    }

    @Test
    public void should_encode_base_difficulty() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("00FFFF"), (0x1D - 0x03));
        final byte[] expectedEncoding = HexUtil.hexStringToByteArray("1D00FFFF");

        // Action
        final byte[] encodedBytes = difficulty.encode();

        // Assert
        TestUtil.assertEqual(expectedEncoding, encodedBytes);
    }

    @Test
    public void should_return_bytes_from_small_exponent_and_significand_0() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("01"), 0x00);
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000001");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_return_bytes_from_small_exponent_and_significand_1() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("FF"), 0x01);
        final byte[] expectedBytes = HexUtil.hexStringToByteArray("000000000000000000000000000000000000000000000000000000000000FF00");

        // Action
        final byte[] bytes = difficulty.getBytes();

        // Assert
        TestUtil.assertEqual(expectedBytes, bytes);
    }

    @Test
    public void should_encode_and_decode_bytes_from_small_exponent_and_significand() {
        // Setup
        final Difficulty difficulty = new ImmutableDifficulty(HexUtil.hexStringToByteArray("FF"), 0x00);
        final byte[] expectedEncodedBytes = HexUtil.hexStringToByteArray("030000FF");
        final byte[] expectedRawBytes = HexUtil.hexStringToByteArray("00000000000000000000000000000000000000000000000000000000000000FF");

        // Action
        final byte[] encodedBytes = difficulty.encode();
        final byte[] rawBytes0 = difficulty.getBytes();
        final byte[] rawBytes1 = ImmutableDifficulty.decode(encodedBytes).getBytes();

        // Assert
        TestUtil.assertEqual(expectedEncodedBytes, encodedBytes);
        TestUtil.assertEqual(expectedRawBytes, rawBytes0);
        TestUtil.assertEqual(expectedRawBytes, rawBytes1);
    }

    @Test
    public void should_encode_and_decode_base_difficulty() {
        // Setup
        final byte[] encodedBaseDifficulty = HexUtil.hexStringToByteArray("1D00FFFF");
        final byte[] expectedRawBytes = HexUtil.hexStringToByteArray("00000000FFFF0000000000000000000000000000000000000000000000000000");

        final Difficulty difficulty0, difficulty1;

        // Action
        difficulty0 = ImmutableDifficulty.decode(encodedBaseDifficulty);
        final byte[] encodedBytes0 = difficulty0.encode();
        difficulty1 = ImmutableDifficulty.decode(encodedBytes0);

        final byte[] rawBytes0 = difficulty0.getBytes();
        final byte[] rawBytes1 = difficulty1.getBytes();

        // Assert
        TestUtil.assertEqual(expectedRawBytes, rawBytes0);
        TestUtil.assertEqual(expectedRawBytes, rawBytes1);
    }

    @Test
    public void should_create_correct_difficulty_ratio() {
        // Setup
        final String difficultyEncodedString = "18031849"; // Difficulty for Block 0000000000000000019215F45AC2190F5316FF2E4BB6ED300104585384269F93
        final BigDecimal expectedDifficultyRatio = new BigDecimal("355264363497.1042");
        final Difficulty difficulty = ImmutableDifficulty.decode(HexUtil.hexStringToByteArray(difficultyEncodedString));

        // Action
        final BigDecimal difficultyRatio = difficulty.getDifficultyRatio();

        // Assert
        Assert.assertEquals(expectedDifficultyRatio, difficultyRatio);
    }

    @Test
    public void multiplying_difficulty_by_1_should_not_change_its_value() {
        // Setup
        final Difficulty difficulty = Difficulty.BASE_DIFFICULTY;

        // Action
        final Difficulty multipliedDifficulty = difficulty.multiplyBy(1.0F);

        // Assert
        Assert.assertEquals(difficulty, multipliedDifficulty);
    }

    @Test
    public void should_recreate_first_difficulty_adjustment_based_on_ratio() {
        // Setup
        final Difficulty difficulty = Difficulty.BASE_DIFFICULTY;
        // NOTE: The listed difficulty adjustment on blockchain.info is 1.18.
        //  However, when investigating the actual difficulty, it is 1.182899...
        //  Command To Verify: bitcoin-cli getblockhash 32256 | xargs bitcoin-cli getblock
        final Float firstDifficultyAdjustment = 1.0F / 1.182899534312841F;
        final Difficulty expectedDifficulty = ImmutableDifficulty.decode(HexUtil.hexStringToByteArray("1D00D86A"));

        // Action
        final Difficulty multipliedDifficulty = difficulty.multiplyBy(firstDifficultyAdjustment);

        // Assert
        Assert.assertEquals(expectedDifficulty, multipliedDifficulty);
    }

    @Test
    public void higher_difficulty_value_should_be_less_difficult() {
        // Setup
        final Difficulty difficulty = ImmutableDifficulty.decode(HexUtil.hexStringToByteArray("1D01B304"));
        final Difficulty baseDifficulty = Difficulty.BASE_DIFFICULTY;

        // Action
        final Boolean isLessThan = difficulty.isLessDifficultThan(baseDifficulty);

        // Assert
        Assert.assertTrue(isLessThan);
    }
}
