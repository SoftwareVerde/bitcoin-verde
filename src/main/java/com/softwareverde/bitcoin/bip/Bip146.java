package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

public class Bip146 {
    // Bitcoin Cash: 2017-11-07 Hard Fork: https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md
    // BIP 146: https://github.com/bitcoin/bips/blob/master/bip-0146.mediawiki

    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= 504032L);
    }

    protected Bip146() { }
}
