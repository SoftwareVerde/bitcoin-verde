package com.softwareverde.bitcoin.miner.pool;

import com.softwareverde.util.type.identifier.Identifier;

public class AccountId extends Identifier {
    public static AccountId wrap(final Long value) {
        if (value == null) { return null; }
        if (value < 1L) { return null; }
        return new AccountId(value);
    }

    protected AccountId(final Long value) {
        super(value);
    }
}
