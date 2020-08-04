package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.ripemd160.Ripemd160Hash;
import com.softwareverde.json.Jsonable;

public interface Script extends Constable<ImmutableScript>, Jsonable {
    Integer MAX_BYTE_COUNT = 0x00100000; // 1 MB
    Script EMPTY_SCRIPT = new ImmutableScript();

    Boolean isValid();
    Ripemd160Hash getHash();
    List<Operation> getOperations();
    int getByteCount();
    ByteArray getBytes();

    Boolean containsNonPushOperations();

    @Override
    ImmutableScript asConst();

    @Override
    int hashCode();

    @Override
    boolean equals(Object object);

    // These functions return the native object implementations for performance...
    int simpleHashCode();
    boolean simpleEquals(Object object);
}
