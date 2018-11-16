package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

public class HF20181115 {
    // Bitcoin Cash: 2018-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2018-nov-upgrade.md
    //                                      https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/op_checkdatasig.md

    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= 556767L);
    }

    protected HF20181115() { }
}
