package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

public class Buip55 {
    // Bitcoin Cash: UAHF - https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/uahf-technical-spec.md

    public static Boolean isEnabled(final Long blockHeight) {
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= 478559L);
    }

    protected Buip55() { }
}
