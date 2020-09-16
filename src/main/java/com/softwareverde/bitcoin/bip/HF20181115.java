package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

class HF20181115 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 556767L;

    // Bitcoin Cash: 2018-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2018-nov-upgrade.md
    //                                      https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/op_checkdatasig.md
    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= ACTIVATION_BLOCK_HEIGHT);
    }

    protected HF20181115() { }
}
