package com.softwareverde.bitcoin.address;

import com.softwareverde.util.type.identifier.Identifier;

public class AddressId extends Identifier {
    public static AddressId wrap(final Long value) {
        if (value == null) { return null; }
        return new AddressId(value);
    }

    protected AddressId(final Long value) {
        super(value);
    }
}
