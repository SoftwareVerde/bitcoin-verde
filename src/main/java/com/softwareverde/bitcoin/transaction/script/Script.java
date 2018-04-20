package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.HexUtil;

public interface Script extends Constable<ImmutableScript>, Jsonable {
    Script EMPTY_SCRIPT = new ImmutableScript();

    Hash getHash();
    List<Operation> getOperations();
    int getByteCount();
    ByteArray getBytes();

    @Override
    ImmutableScript asConst();
}
