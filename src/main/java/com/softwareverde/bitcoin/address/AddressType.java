package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.transaction.script.ScriptType;

public enum AddressType {
    P2PKH, P2SH;

    public static AddressType fromBase58Prefix(final byte b) {
        if (b == 0x00) { return P2PKH; }
        if (b == 0x05) { return P2SH; }
        return null;
    }

    public static AddressType fromBase32Prefix(final byte b) {
        if ( (b == 0x00) || (b == 0x02) ) { return P2PKH; }
        if ( (b == 0x01) || (b == 0x03) ) { return P2SH; }
        return null;
    }

    public static AddressType fromScriptType(final ScriptType scriptType) {
        if (scriptType == ScriptType.PAY_TO_PUBLIC_KEY_HASH) { return P2PKH; }
        if (scriptType == ScriptType.PAY_TO_SCRIPT_HASH) { return P2PKH; }
        return null;
    }

    public byte getBase58Prefix() {
        if (this == P2PKH) { return 0x00; }
        if (this == P2SH) { return 0x05; }
        throw new RuntimeException("Invalid Address Type."); // Cannot happen.
    }

    public byte getBase32Prefix(final Boolean isTokenAware) {
        if (this == P2PKH) { return (byte) (isTokenAware ? 0x02 : 0x00); }
        if (this == P2SH) { return (byte) (isTokenAware ? 0x03 : 0x01); }
        throw new RuntimeException("Invalid Address Type."); // Cannot happen.
    }

    public Boolean isTokenAware(final byte b) {
        return (b == 0x02 || b == 0x03);
    }
}
