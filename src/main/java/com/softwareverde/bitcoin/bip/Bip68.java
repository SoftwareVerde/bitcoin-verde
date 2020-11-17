package com.softwareverde.bitcoin.bip;

class Bip68 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 419328L;

    // Relative Lock-Time Using Consensus-Enforced Sequence Numbers - https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki
    public static Boolean isEnabled(final Long blockHeight) {
        return (blockHeight >= ACTIVATION_BLOCK_HEIGHT); // https://www.reddit.com/r/Bitcoin/comments/4r9tiv/csv_soft_fork_has_activated_as_of_block_419328
    }

    protected Bip68() { }
}
