package com.softwareverde.bitcoin.bip;

public class HF20181115SV {
    // Bitcoin Cash: 2018-11-15 Hard Fork (Satoshi's Vision):   https://github.com/bitcoin-sv/bitcoin-sv/blob/master/doc/release-notes.md
    //                                                          https://github.com/bitcoincashorg/bitcoincash.org/blob/3d86e3f6a8726ebbe2076a96e3d58d9d6e18b0f4/spec/20190515-reenabled-opcodes.md

    public static Boolean isEnabled(final Long blockHeight) {
        // return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= 556767L);
        return false;
    }

    protected HF20181115SV() { }
}
