package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.type.bytearray.overflow.ImmutableOverflowingByteArray;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;

public class ImmutableScript extends ImmutableOverflowingByteArray implements Script, Const {

    public ImmutableScript(final byte[] bytes) {
        super(bytes);
    }

    @Override
    public Hash getHash() {
        final byte[] hashBytes = BitcoinUtil.ripemd160(BitcoinUtil.sha256(_bytes));
        return MutableHash.wrap(hashBytes);
    }

    @Override
    public Script subScript(final int opcodeIndex) {
        final int newByteCount = (_bytes.length - opcodeIndex);

        if (newByteCount <= 0) {
            return Script.EMPTY_SCRIPT;
        }

        final byte[] copiedBytes = ByteUtil.copyBytes(_bytes, opcodeIndex, newByteCount);
        return new ImmutableScript(copiedBytes);
    }

    @Override
    public ImmutableScript asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final Json json = new Json();
        json.put("bytes", HexUtil.toHexString(_bytes));

        final Json operationsJson;
        {
            final List<Operation> operations = ScriptReader.getOperationList(this);
            if (operations != null) {
                operationsJson = new Json();
                for (final Operation operation : operations) {
                    operationsJson.add(operation);
                }
            }
            else {
                operationsJson = null;
            }
        }
        json.put("operations", operationsJson);

        return json;
    }
}
