package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.util.HexUtil;

import java.math.BigDecimal;
import java.math.BigInteger;

public class ImmutableDifficulty implements Difficulty, Const {
    public static final Long MAX_SIGNIFICAND_VALUE = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray("7FFFFF"));

    private final Integer _exponent;
    private final byte[] _significand = new byte[3];

    private byte[] _cachedBytes = null;

    protected static ImmutableDifficulty fromBigInteger(final BigInteger bigInteger) {
        final int significandByteCount = 3;

        final byte[] bytes = bigInteger.toByteArray();
        final int exponent = (bytes.length - significandByteCount);
        final byte[] significand = ByteUtil.copyBytes(bytes, 0, significandByteCount);
        // Since significand is normally* interpreted as a signed value, its max value is 0x7FFFFF.
        // If significand is greater than this value, then shift the significand right one, and increase the exponent.
        //  * Why it's considered signed seems unjustified. So it goes.

        // HexUtil.toHexString(Difficulty.BASE_DIFFICULTY._toBigDecimal().toBigInteger().toByteArray()) returns:
        //           00FFFF0000000000000000000000000000000000000000000000000000

        // HexUtil.toHexString(Difficulty.BASE_DIFFICULTY._convertToBytes()) returns:
        //     00000000FFFF0000000000000000000000000000000000000000000000000000

        // HexUtil.toHexString(Difficulty.BASE_DIFFICULTY._toBigInteger().toByteArray()) returns:
        //           00FFFF0000000000000000000000000000000000000000000000000000

        if (ByteUtil.bytesToLong(significand) >= MAX_SIGNIFICAND_VALUE) {
            // Shifting the value will lose precision.
            //  The value will go from:
            //     00000000FFFF0000000000000000000000000000000000000000000000000000 (1D00FFFF)
            //  To:
            //     00000000FF000000000000000000000000000000000000000000000000000000 (1E0000FF)
            final byte[] shiftedSignificand = new byte[] { 0x00, significand[0], significand[1] };
            final int shiftedExponent = (exponent + 1);
            return new ImmutableDifficulty(shiftedSignificand, shiftedExponent);
        }

        return new ImmutableDifficulty(significand, exponent);

    }

    public static ImmutableDifficulty decode(final byte[] encodedBytes) {
        if (encodedBytes.length != 4) { return null; }
        return new ImmutableDifficulty(ByteUtil.copyBytes(encodedBytes, 1, 3), (ByteUtil.byteToInteger(encodedBytes[0]) - 3));
    }

    protected BigInteger _toBigInteger() {
        return new BigInteger(_convertToBytes());
    }

    protected BigDecimal _toBigDecimal() {
        final BigInteger bigInteger = _toBigInteger();
        final BigDecimal bigDecimal = new BigDecimal(bigInteger);
        // NOTE: Invoking the BigDecimal constructor with the scale provided is NOT the same as setting its scale afterwards.
        //  Therefore, think twice before changing/condensing this.
        //  The BigDecimal(BigInteger, Scale) constructor sets the value to 10^Scale less than what is perceived.
        return bigDecimal.setScale(4, BigDecimal.ROUND_UNNECESSARY); // setScale(4);
    }

    protected byte[] _encode() {
        final byte[] bytes = new byte[4];
        bytes[0] = (byte) (_exponent + 3);
        ByteUtil.setBytes(bytes, _significand, 1);
        return bytes;
    }

    public ImmutableDifficulty(final byte[] significand, final Integer exponent) {
        _exponent = exponent;

        final int copyCount = Math.min(_significand.length, significand.length);
        for (int i=0; i<copyCount; ++i) {
            _significand[(_significand.length - i) - 1] = significand[(significand.length - i) - 1];
        }
    }

    public ImmutableDifficulty(final Difficulty difficulty) {
        _exponent = difficulty.getExponent();
        ByteUtil.setBytes(_significand, difficulty.getSignificand());
    }

    protected byte[] _convertToBytes() {
        final byte[] bytes = new byte[32];
        ByteUtil.setBytes(bytes, _significand, (32 - _exponent - _significand.length));
        return bytes;
    }

    @Override
    public byte[] getBytes() {
        return _convertToBytes();
    }

    @Override
    public byte[] encode() {
        return _encode();
    }

    @Override
    public Integer getExponent() { return _exponent; }

    @Override
    public byte[] getSignificand() { return ByteUtil.copyBytes(_significand); }

    @Override
    public Boolean isSatisfiedBy(final Hash hash) {
        if (_cachedBytes == null) {
            _cachedBytes = _convertToBytes();
        }

        for (int i=0; i<_cachedBytes.length; ++i) {
            // if (i > 2) Logger.log(HexUtil.toHexString(_cachedBytes) + " " + hash);
            final int difficultyByte = ByteUtil.byteToInteger(_cachedBytes[i]);
            final int sha256Byte = ByteUtil.byteToInteger(hash.getByte(i));
            if (sha256Byte == difficultyByte) { continue; }
            return (sha256Byte < difficultyByte);
        }

        return true;
    }

    @Override
    public BigDecimal getDifficultyRatio() {
        final BigDecimal currentValue = _toBigDecimal();
        final BigDecimal baseDifficultyValue = Difficulty.BASE_DIFFICULTY._toBigDecimal();
        return baseDifficultyValue.divide(currentValue, BigDecimal.ROUND_HALF_UP);
    }

    @Override
    public Difficulty multiplyBy(final double difficultyAdjustment) {
        final BigDecimal currentValue = _toBigDecimal();
        final BigDecimal bigDecimal = currentValue.multiply(BigDecimal.valueOf(difficultyAdjustment));
        return fromBigInteger(bigDecimal.toBigInteger());
    }

    @Override
    public ImmutableDifficulty asConst() {
        return this;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof Difficulty)) { return false; }

        final Difficulty difficulty = (Difficulty) object;
        if (! _exponent.equals(difficulty.getExponent())) { return false; }

        return ByteUtil.areEqual(_significand, difficulty.getSignificand());
    }

    @Override
    public String toString() {
        return HexUtil.toHexString(_encode());
    }
}
