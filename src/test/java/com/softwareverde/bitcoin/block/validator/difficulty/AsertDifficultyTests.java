package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class AsertDifficultyTests {

    public static class TestVector {
        public final BigInteger height;
        public final BigInteger time;
        public final Difficulty target;

        public TestVector(final BigInteger height, final BigInteger time, final String encodedTarget) {
            this.height = height;
            this.time = time;
            this.target = Difficulty.decode(ByteArray.fromHexString(encodedTarget));
        }
    }

    public static List<TestVector> inflateTests(final String testsString) {
        final MutableList<TestVector> tests = new MutableList<TestVector>();
        final String[] testVectors = testsString.split("\n");
        for (final String vector : testVectors) {
            if (vector.startsWith("#")) { continue; }
            final String[] parts = vector.split(" ");
            final BigInteger height = new BigInteger(parts[1], 10);
            final BigInteger time = new BigInteger(parts[2], 10);
            final String encodedTargetString = parts[3].replace("0x", "");
            tests.add(new TestVector(height, time, encodedTargetString));
        }

        return tests;
    }

    protected static void runCalculations(final List<TestVector> tests, final Difficulty anchorBits, final BigInteger anchorTime, final BigInteger anchorHeight) {
        for (TestVector testVector : tests) {
            final BigInteger refTarget = Difficulty.toBigInteger(anchorBits);
            final Difficulty nextTarget = AsertDifficultyCalculator.computeAsertTarget(refTarget, anchorTime, anchorHeight, testVector.time, testVector.height);
            Assert.assertEquals(testVector.target, nextTarget);
        }
    }

    @Test
    public void run01() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run01"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1D00FFFF"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run02() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run02"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1A2B3C4D"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run03() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run03"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("01010000"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run04() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run04"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("01010000"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run05() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run05"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1D00FFFF"));
        runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run06() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run06"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run07() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run07"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run08() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run08"));
        final BigInteger anchorHeight = BigInteger.ONE;
        final BigInteger anchorTime = BigInteger.ZERO;
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run09() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run09"));
        final BigInteger anchorHeight = BigInteger.valueOf(2147483642);
        final BigInteger anchorTime = BigInteger.valueOf(1234567290);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run10() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run10"));
        final BigInteger anchorHeight = BigInteger.valueOf(9223372036854775802L);
        final BigInteger anchorTime = BigInteger.valueOf(2147483047);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run11() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run11"));
        final BigInteger anchorHeight = BigInteger.valueOf(1L);
        final BigInteger anchorTime = BigInteger.valueOf(0);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }

    @Test
    public void run12() {
        final List<TestVector> tests = AsertDifficultyTests.inflateTests(IoUtil.getResource("/aserti3-2d/test_vectors/run12"));
        final BigInteger anchorHeight = BigInteger.valueOf(1L);
        final BigInteger anchorTime = BigInteger.valueOf(10000);
        final Difficulty anchorBits = Difficulty.decode(ByteArray.fromHexString("1802AEE8"));
        AsertDifficultyTests.runCalculations(tests, anchorBits, anchorTime, anchorHeight);
    }
}