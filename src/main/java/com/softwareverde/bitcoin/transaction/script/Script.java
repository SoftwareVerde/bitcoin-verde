package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Jsonable;

public interface Script extends ByteArray, Jsonable {
    Script EMPTY_SCRIPT = new ImmutableScript(new byte[0]);

    Hash getHash();

    @Override
    ImmutableScript asConst();
}
