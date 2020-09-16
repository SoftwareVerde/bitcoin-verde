package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

class Buip55 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 478559L;

    // Bitcoin Cash: UAHF - https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/uahf-technical-spec.md
    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= ACTIVATION_BLOCK_HEIGHT);
    }

    protected Buip55() { }
}
