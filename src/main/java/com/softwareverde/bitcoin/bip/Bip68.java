package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.transaction.Transaction;

public class Bip68 {
    // Tx Input Sequence Numbers - https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki
    public static Boolean isEnabled(final Transaction transaction) {
        return (transaction.getVersion() > 2);
    }

    protected Bip68() { }
}
