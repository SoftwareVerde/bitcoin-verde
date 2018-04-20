package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.HexUtil;

public interface Script extends Constable<ImmutableScript>, Jsonable {
    Script EMPTY_SCRIPT = new ImmutableScript(new byte[0]);

    static List<Operation> getOperationList(final ByteArray bytes) {
        final OperationInflater operationInflater = new OperationInflater();
        final ImmutableListBuilder<Operation> immutableListBuilder = new ImmutableListBuilder<Operation>();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        while (byteArrayReader.remainingByteCount() > 0) {
            final int scriptPosition = scriptReader.getPosition();
            final Operation opcode = operationInflater.fromScriptReader(scriptReader);
            if (opcode == null) {
                scriptReader.setPosition(scriptPosition);
                Logger.log("NOTICE: Unknown opcode. 0x"+ HexUtil.toHexString(new byte[] { scriptReader.peakNextByte() }));
                return null;
            }

            immutableListBuilder.add(opcode);
        }
        return immutableListBuilder.build();
    }

    Hash getHash();
    byte[] getBytes();

    @Override
    ImmutableScript asConst();
}
