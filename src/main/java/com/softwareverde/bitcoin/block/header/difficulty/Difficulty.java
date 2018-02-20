package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.type.hash.Hash;

import java.math.BigDecimal;

public interface Difficulty {
    static final Integer BASE_DIFFICULTY_EXPONENT = (0x1D - 0x03);
    static final Integer BASE_DIFFICULTY_SIGNIFICAND = 0x00FFFF;

    Integer getExponent();
    byte[] getSignificand();

    Boolean isSatisfiedBy(final Hash hash);
    BigDecimal getDifficultyRatio();

    byte[] getBytes();
    byte[] encode();
}
