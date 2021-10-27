package com.softwareverde.bitcoin.bloomfilter;

public enum UpdateBloomFilterMode {
    READ_ONLY(0),  // The filter is not adjusted when a match is found...
    UPDATE_ALL(1), // The filter is updated to include the TransactionOutput's TransactionOutputIdentifier when a match to any data element in the LockingScript is found.
    P2PK_P2MS(2);  // The filter is updated to include the TransactionOutput TransactionOutputIdentifier only if a data element in the LockingScript is matched, and the script is either P2PK or MultiSig.

    public static UpdateBloomFilterMode valueOf(final byte value) {
        final int maskedValue = (0x03 & value);
        for (final UpdateBloomFilterMode filterMode : UpdateBloomFilterMode.values()) {
            if (maskedValue == filterMode.getValue()) { return filterMode; }
        }

        return null;
    }

    // https://github.com/bitcoinxt/bitcoinxt/pull/139/commits/b807723f2e577bca03e93476541d1ab31eb13907
    // "Third bit of nFlags indicates filter wants to be updated with mempool ancestors for its entries."
    public static Boolean shouldUpdateFromMemoryPoolAncestors(final int value) {
        return ((value & 0x04) != 0x00);
    }

    protected final byte _value;
    UpdateBloomFilterMode(final int value) {
        _value = (byte) value;
    }

    public byte getValue() { return _value; }
}
