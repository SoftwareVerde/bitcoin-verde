package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class AsertDifficultyTests {

    public static class TestVector {
        public final BigInteger blockHeight;
        public final MedianBlockTime medianBlockTime;
        public final Difficulty difficulty;

        public TestVector(final BigInteger blockHeight, final MedianBlockTime medianBlockTime, final Difficulty difficulty) {
            this.blockHeight = blockHeight;
            this.medianBlockTime = medianBlockTime;
            this.difficulty = difficulty;
        }
    }

    public static List<TestVector> inflateTests(final String testsString) {
        final MutableList<TestVector> tests = new MutableList<TestVector>();
        final String[] testVectors = testsString.split("\n");
        for (final String vector : testVectors) {
            if (vector.startsWith("#")) { continue; }
            final String[] parts = vector.split(" ");
            final BigInteger blockHeight = new BigInteger(parts[1], 10);
            final Long blockTime = Util.parseLong(parts[2]);
            final String encodedDifficulty = parts[3].replace("0x", "");

            final MedianBlockTime medianBlockTime = MedianBlockTime.fromSeconds(blockTime);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString(encodedDifficulty));
            tests.add(new TestVector(blockHeight, medianBlockTime, difficulty));
        }

        return tests;
    }

    protected static void runCalculations(final List<TestVector> tests, final Difficulty anchorBits, final MedianBlockTime anchorTime, final BigInteger anchorHeight) {
        final AsertDifficultyCalculator asertDifficultyCalculator = new AsertDifficultyCalculator();
        for (TestVector testVector : tests) {
            final AsertReferenceBlock referenceBlock = new AsertReferenceBlock(anchorHeight, anchorTime, anchorBits);
            final Difficulty nextTarget = asertDifficultyCalculator._computeAsertTarget(referenceBlock, testVector.medianBlockTime, testVector.blockHeight);
            Assert.assertEquals(testVector.difficulty, nextTarget);
        }
    }

    @Test
    public void run01() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run01"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1D00FFFF"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run02() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run02"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1A2B3C4D"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run03() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run03"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("01010000"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run04() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run04"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("01010000"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run05() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run05"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1D00FFFF"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run06() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run06"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run07() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run07"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run08() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run08"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run09() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run09"));
        final BigInteger anchorHeight = BigInteger.valueOf(2147483642L);
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(1234567290L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run10() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run10"));
        final BigInteger anchorHeight = BigInteger.valueOf(9223372036854775802L);
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(2147483047L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run11() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run11"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(0L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run12() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run12"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final MedianBlockTime anchorTime = MedianBlockTime.fromSeconds(10000L);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }
}