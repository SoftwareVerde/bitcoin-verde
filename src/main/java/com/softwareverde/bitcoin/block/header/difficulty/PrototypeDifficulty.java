package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class PrototypeDifficulty extends ImmutableDifficulty {

    public PrototypeDifficulty(final ByteArray significand, final Integer exponent) {
        super(significand, exponent);
    }

    public PrototypeDifficulty(final Difficulty difficulty) {
        super(difficulty);
    }

    @Override
    public Boolean isSatisfiedBy(final Sha256Hash hash) {
        return true;
    }
}
