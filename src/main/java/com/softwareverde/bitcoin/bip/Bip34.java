package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

class Bip34 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 227835L;

    // Block v2. Height in Coinbase
    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) > ACTIVATION_BLOCK_HEIGHT);
    }

    protected Bip34() { }
}
