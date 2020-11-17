package com.softwareverde.bitcoin.bip;

import com.softwareverde.util.Util;

class Bip65 {
    public static final Long ACTIVATION_BLOCK_HEIGHT = 388167L;

    // OP_CHECKLOCKTIMEVERIFY -- https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki
    public static Boolean isEnabled(final Long blockHeight) {
        // http://data.bitcoinity.org/bitcoin/block_version/5y?c=block_version&r=day&t=l
        // https://www.reddit.com/r/Bitcoin/comments/3vwvxq/a_new_era_of_op_hodl_is_on_us_bip65_has_activated/
        // https://blockchain.info/block/000000000000000005de239ca0d781cc9e78add7c48e94e2264223aa31fc6256

        // The last v3 block was mined at height 388166.  Technically, Bip65 likely activated a few days before this.
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) >= ACTIVATION_BLOCK_HEIGHT);
    }

    protected Bip65() { }
}
