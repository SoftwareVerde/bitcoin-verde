package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

public class Bip16 {
    // Pay to Script Hash - https://github.com/bitcoin/bips/blob/master/bip-0016.mediawiki
    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= 173805L);
    }

    protected Bip16() { }
}
