package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

public class Bip34 {
    // Block v2. Height in Coinbase
    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) > 227835L);
    }

    protected Bip34() { }
}
