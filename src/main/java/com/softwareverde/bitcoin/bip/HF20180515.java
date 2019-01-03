package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

public class HF20180515 {
    // Bitcoin Cash: 2018-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/may-2018-hardfork.md
    //                                      https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/may-2018-reenabled-opcodes.md

    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= 530359L);
    }

    protected HF20180515() { }
}
