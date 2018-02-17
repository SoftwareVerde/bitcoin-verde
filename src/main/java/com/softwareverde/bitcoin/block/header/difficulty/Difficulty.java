package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.util.ByteUtil;

import java.math.BigDecimal;

public class Difficulty {
    public static final Integer BASE_DIFFICULTY_EXPONENT = (0x1D - 0x03);
    public static final Integer BASE_DIFFICULTY_SIGNIFICAND = 0x00FFFF;

    private Integer _exponent;
    private final byte[] _significand = new byte[3];

    public static Difficulty decode(final byte[] encodedBytes) {
        if (encodedBytes.length != 4) { return null; }
        return new Difficulty(ByteUtil.copyBytes(encodedBytes, 1, 3), (ByteUtil.byteToInteger(encodedBytes[0]) - 3));
    }

    public Difficulty(final byte[] significand, final Integer exponent) {
        _exponent = exponent;

        final int copyCount = Math.min(_significand.length, significand.length);
        for (int i=0; i<copyCount; ++i) {
            _significand[(_significand.length - i) - 1] = significand[(significand.length - i) - 1];
        }
    }

    private byte[] _convertToBytes() {
        final byte[] bytes = new byte[32];
        ByteUtil.setBytes(bytes, _significand, (32 - _exponent - _significand.length));
        return bytes;
    }

    public byte[] getBytes() {
        return _convertToBytes();
    }

    public byte[] encode() {
        final byte[] bytes = new byte[4];
        bytes[0] = (byte) (_exponent + 3);
        ByteUtil.setBytes(bytes, _significand, 1);
        return bytes;
    }

    public Boolean isSatisfiedBy(final byte[] sha256) {
        if (sha256.length != 32) { return false; }

        final byte[] bytes = _convertToBytes();

        for (int i=0; i<bytes.length; ++i) {
            final int difficultyByte = ByteUtil.byteToInteger(bytes[i]);
            final int sha256Byte = ByteUtil.byteToInteger(sha256[i]);
            if (sha256Byte == difficultyByte) { continue; }
            return (sha256Byte < difficultyByte);
        }

        return true;
    }

    public BigDecimal getDifficultyRatio() {
        final BigDecimal currentValue = BigDecimal.valueOf(ByteUtil.bytesToLong(_significand), _exponent);
        final BigDecimal baseDifficultyValue = BigDecimal.valueOf(BASE_DIFFICULTY_SIGNIFICAND, BASE_DIFFICULTY_EXPONENT);
        return baseDifficultyValue.divide(currentValue, BigDecimal.ROUND_HALF_UP);
    }

    public Difficulty copy() {
        return new Difficulty(_significand, _exponent);
    }
}
