package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

class HF20180515 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 530359L;

    // Bitcoin Cash: 2018-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/may-2018-hardfork.md
    //                                      https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/may-2018-reenabled-opcodes.md
    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= ACTIVATION_BLOCK_HEIGHT);
    }

    protected HF20180515() { }
}
