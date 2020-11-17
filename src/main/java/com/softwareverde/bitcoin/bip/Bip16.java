package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

class Bip16 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 173805L;

    // Pay to Script Hash - https://github.com/bitcoin/bips/blob/master/bip-0016.mediawiki
    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= ACTIVATION_BLOCK_HEIGHT);
    }

    protected Bip16() { }
}
