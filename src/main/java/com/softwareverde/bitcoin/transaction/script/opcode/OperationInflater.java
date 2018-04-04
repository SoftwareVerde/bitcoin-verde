package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class OperationInflater {
    public Operation fromScriptReader(final ScriptReader scriptReader) {
        if (! scriptReader.hasNextByte()) { return null; }

        final byte b = scriptReader.peakNextByte();
        final Operation.Type type = Operation.Type.getType(b);
        if (type == null) { return null; }

        switch (type) {
            case OP_PUSH:           { return PushOperation.fromScriptReader(scriptReader); }
            case OP_DYNAMIC_VALUE:  { return DynamicValueOperation.fromScriptReader(scriptReader); }
            case OP_CONTROL:        { break; } // TODO
            case OP_STACK:          { return StackOperation.fromScriptReader(scriptReader); }
            case OP_STRING:         { break; } // TODO
            case OP_BITWISE:        { break; } // TODO
            case OP_COMPARISON:     { return ComparisonOperation.fromScriptReader(scriptReader); }
            case OP_ARITHMETIC:     { break; } // TODO
            case OP_CRYPTOGRAPHIC:  { return CryptographicOperation.fromScriptReader(scriptReader); }
            case OP_LOCK_TIME:      { return LockTimeOperation.fromScriptReader(scriptReader); }
            case OP_NOTHING:        { return NothingOperation.fromScriptReader(scriptReader); }
        }

        Logger.log("Unimplemented Opcode Type: "+ type + " (0x" + HexUtil.toHexString(new byte[] { b }) + ")");
        return null;
    }
}
