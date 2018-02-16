package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Assert;
import org.junit.Test;

public class DifficultyEncoderTests {
    @Test
    public void should_decode_zero_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] encodedDifficulty = BitcoinUtil.hexStringToByteArray("01003456");
        final Long expectedDifficulty = 0x00L;

        // Action
        final Long difficulty = difficultyEncoder.decodeDifficulty(encodedDifficulty);

        // Assert
        Assert.assertEquals(expectedDifficulty, difficulty);
    }

    @Test
    public void should_decode_mainnet_1_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] encodedDifficulty = BitcoinUtil.hexStringToByteArray("1D00FFFF");
        final Long expectedDifficulty = 0x01L;

        // Action
        final Long difficulty = difficultyEncoder.decodeDifficulty(encodedDifficulty);

        // Assert
        Assert.assertEquals(expectedDifficulty, difficulty);
    }

    @Test
    public void should_encode_difficulty_0x1234560000() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] expectedDifficulty = BitcoinUtil.hexStringToByteArray("05123456");
        final Long decodedDifficulty = 0x1234560000L;

        // Action
        final byte[] encodedDifficulty = difficultyEncoder.encodeDifficulty(decodedDifficulty);

        // Assert
        TestUtil.assertEqual(expectedDifficulty, encodedDifficulty);
    }

    @Test
    public void should_encode_zero_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final Long decodedDifficulty = 0x00L;

        // Action
        final byte[] encodedDifficultyBytes = difficultyEncoder.encodeDifficulty(decodedDifficulty);

        // Assert
        Assert.assertEquals(decodedDifficulty, difficultyEncoder.decodeDifficulty(encodedDifficultyBytes));
    }

    @Test
    public void should_decode_18_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] encodedDifficulty = BitcoinUtil.hexStringToByteArray("01123456");
        final Long expectedDifficulty = 0x12L;

        // Action
        final Long difficulty = difficultyEncoder.decodeDifficulty(encodedDifficulty);

        // Assert
        Assert.assertEquals(expectedDifficulty, difficulty);
    }

    @Test
    public void should_encode_18_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final Long decodedDifficulty = 0x12L;

        // Action
        final byte[] encodedDifficulty = difficultyEncoder.encodeDifficulty(decodedDifficulty);

        // Assert
        Assert.assertEquals(decodedDifficulty, difficultyEncoder.decodeDifficulty(encodedDifficulty));
    }

    @Test
    public void should_decode_128_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] encodedDifficulty = BitcoinUtil.hexStringToByteArray("02008000");
        final Long expectedDifficulty = 0x80L;

        // Action
        final Long difficulty = difficultyEncoder.decodeDifficulty(encodedDifficulty);

        // Assert
        Assert.assertEquals(expectedDifficulty, difficulty);
    }

    @Test
    public void should_encode_128_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] expectedDifficulty = BitcoinUtil.hexStringToByteArray("02008000");
        final Long decodedDifficulty = 0x80L;

        // Action
        final byte[] encodedDifficulty = difficultyEncoder.encodeDifficulty(decodedDifficulty);

        // Assert
        TestUtil.assertEqual(expectedDifficulty, encodedDifficulty);
    }

    @Test
    public void should_decode_2452881408_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] encodedDifficulty = BitcoinUtil.hexStringToByteArray("05009234");
        final Long expectedDifficulty = 0x92340000L;

        // Action
        final Long difficulty = difficultyEncoder.decodeDifficulty(encodedDifficulty);

        // Assert
        Assert.assertEquals(expectedDifficulty, difficulty);
    }

    @Test
    public void should_encode_2452881408_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] expectedDifficulty = BitcoinUtil.hexStringToByteArray("05009234");
        final Long decodedDifficulty = 0x92340000L;

        // Action
        final byte[] encodedDifficulty = difficultyEncoder.encodeDifficulty(decodedDifficulty);

        // Assert
        TestUtil.assertEqual(expectedDifficulty, encodedDifficulty);
    }

    @Test
    public void should_decode_neg_305419776_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] encodedDifficulty = BitcoinUtil.hexStringToByteArray("04923456");
        final Long expectedDifficulty = (0x12345600L | 0x80000000L); // Make Negative

        // Action
        final Long difficulty = difficultyEncoder.decodeDifficulty(encodedDifficulty);

        // Assert
        Assert.assertEquals(expectedDifficulty, difficulty);
    }

    @Test
    public void should_decode_305419776_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] encodedDifficulty = BitcoinUtil.hexStringToByteArray("04123456");
        final Long expectedDifficulty = 0x12345600L;

        // Action
        final Long difficulty = difficultyEncoder.decodeDifficulty(encodedDifficulty);

        // Assert
        Assert.assertEquals(expectedDifficulty, difficulty);
    }

    @Test
    public void should_encode_305419776_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final byte[] expectedDifficulty = BitcoinUtil.hexStringToByteArray("04123456");
        final Long decodedDifficulty = 0x12345600L;

        // Action
        final byte[] encodedDifficulty = difficultyEncoder.encodeDifficulty(decodedDifficulty);

        // Assert
        TestUtil.assertEqual(expectedDifficulty, encodedDifficulty);
    }

    @Test
    public void should_encode_and_decode_very_large_difficulty() {
        // Setup
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();
        final Long decodedDifficulty = 0x7FFFFFFFFFFFFFFFL;

        // Action
        final byte[] encodedDifficultyBytes = difficultyEncoder.encodeDifficulty(decodedDifficulty);

        // Assert
        final long precisionMask = 0xFFFFFF0000000000L; // Algorithm's precision is only good for 3 bytes.
        Assert.assertEquals(decodedDifficulty & precisionMask, difficultyEncoder.decodeDifficulty(encodedDifficultyBytes) & precisionMask);
    }
}
