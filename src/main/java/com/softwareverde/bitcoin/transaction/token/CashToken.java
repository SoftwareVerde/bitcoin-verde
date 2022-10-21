package com.softwareverde.bitcoin.transaction.token;

public class CashToken {
    public static final byte PREFIX = (byte) 0xEF;

    public enum NftCapability {
        NONE(0x00), MUTABLE(0x01), MINTING(0x02);

        public final byte flag;

        NftCapability(final int flag) {
            this.flag = (byte) flag;
        }

        public static NftCapability fromByte(final byte b) {
            for (final NftCapability nftCapability : NftCapability.values()) {
                if (nftCapability.flag == b) {
                    return nftCapability;
                }
            }
            return null;
        }
    }
}
