package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.type.hash.ripemd160.Ripemd160Hash;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Jsonable;

public interface Script extends Constable<ImmutableScript>, Jsonable {
    Script EMPTY_SCRIPT = new ImmutableScript();

    Ripemd160Hash getHash();
    List<Operation> getOperations();
    int getByteCount();
    ByteArray getBytes();

    @Override
    ImmutableScript asConst();
}
