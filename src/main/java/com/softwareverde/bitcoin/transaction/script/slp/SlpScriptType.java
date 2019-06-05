package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.Util;

public enum SlpScriptType {
    GENESIS     ("47454E45534953"),
    SEND        ("53454E44"),
    MINT        ("4D494E54");

    public static final ByteArray LOKAD_ID = ByteArray.fromHexString("534C5000");
    public static final ByteArray TOKEN_TYPE = ByteArray.fromHexString("01");

    public static SlpScriptType fromBytes(final ByteArray byteArray) {
        for (final SlpScriptType slpScriptType : SlpScriptType.values()) {
            if (Util.areEqual(slpScriptType.getBytes(), byteArray)) {
                return slpScriptType;
            }
        }

        return null;
    }

    protected final ByteArray _value;
    SlpScriptType(final String value) {
        _value = ByteArray.fromHexString(value);
    }

    public ByteArray getBytes() {
        return _value;
    }
}
