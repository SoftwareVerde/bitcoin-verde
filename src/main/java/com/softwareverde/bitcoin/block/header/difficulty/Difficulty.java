package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.Constable;

import java.math.BigDecimal;

public interface Difficulty extends Constable<ImmutableDifficulty> {
    Integer BASE_DIFFICULTY_EXPONENT = (0x1D - 0x03);
    byte[] BASE_DIFFICULTY_SIGNIFICAND = new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF };
    ImmutableDifficulty BASE_DIFFICULTY = new ImmutableDifficulty(BASE_DIFFICULTY_SIGNIFICAND, BASE_DIFFICULTY_EXPONENT);

    Integer getExponent();
    byte[] getSignificand();

    Boolean isSatisfiedBy(final Hash hash);
    Boolean isLessDifficultThan(final Difficulty difficulty);
    BigDecimal getDifficultyRatio();

    Difficulty multiplyBy(final double difficultyAdjustment);

    byte[] getBytes();
    byte[] encode();

    @Override
    ImmutableDifficulty asConst();
}
