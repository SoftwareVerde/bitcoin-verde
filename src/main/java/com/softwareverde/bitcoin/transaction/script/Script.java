package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.type.bytearray.ByteArray;

public interface Script extends ByteArray {
    Script EMPTY_SCRIPT = new ImmutableScript(new byte[0]);

    @Override
    ImmutableScript asConst();
}
