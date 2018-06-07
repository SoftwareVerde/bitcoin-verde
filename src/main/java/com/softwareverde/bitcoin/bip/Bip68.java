package com.softwareverde.bitcoin.bip;

public class Bip68 {
    // Relative Lock-Time Using Consensus-Enforced Sequence Numbers - https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki
    public static Boolean isEnabled(final Long blockHeight) {
        return (blockHeight >= 419328L); // https://www.reddit.com/r/Bitcoin/comments/4r9tiv/csv_soft_fork_has_activated_as_of_block_419328
    }

    protected Bip68() { }
}
