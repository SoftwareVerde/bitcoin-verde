package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

class HF20171113 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 504032L;

    // Bitcoin Cash: 2017-11-07 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md
    // BIP 146:                             https://github.com/bitcoin/bips/blob/master/bip-0146.mediawiki

    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= ACTIVATION_BLOCK_HEIGHT);
    }

    protected HF20171113() { }
}
