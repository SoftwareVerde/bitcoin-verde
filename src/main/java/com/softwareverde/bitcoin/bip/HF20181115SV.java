package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

public class HF20181115SV {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 556767L;
    public static final Boolean IS_DISABLED = true;

    // Bitcoin Cash: 2018-11-15 Hard Fork (Satoshi's Vision):   https://github.com/bitcoin-sv/bitcoin-sv/blob/master/doc/release-notes.md
    //                                                          https://github.com/bitcoincashorg/bitcoincash.org/blob/3d86e3f6a8726ebbe2076a96e3d58d9d6e18b0f4/spec/20190515-reenabled-opcodes.md
    public static Boolean isEnabled(final Long blockHeight) {
        if (IS_DISABLED) { return false; }
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= ACTIVATION_BLOCK_HEIGHT);
    }

    protected HF20181115SV() { }
}
