package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.io.Logger;

public class OperationInflater {
    public Operation fromScriptReader(final ScriptReader scriptReader) {
        if (! scriptReader.hasNextByte()) { return null; }

        final Operation.Type type = Operation.Type.getType(scriptReader.peakNextByte());
        if (type == null) { return null; }

        switch (type) {
            case OP_PUSH:           { return PushOperation.fromScriptReader(scriptReader); }
            case OP_DYNAMIC_VALUE:  { return DynamicValueOperation.fromScriptReader(scriptReader); }
            case OP_CONTROL:        { break; } // TODO
            case OP_STACK:          { break; } // TODO
            case OP_STRING:         { break; } // TODO
            case OP_BITWISE:        { break; } // TODO
            case OP_COMPARISON:     { return ComparisonOperation.fromScriptReader(scriptReader); }
            case OP_ARITHMETIC:     { break; } // TODO
            case OP_CRYPTOGRAPHIC:  { return CryptographicOperation.fromScriptReader(scriptReader); }
            case OP_LOCK_TIME:      { break; } // TODO
            case OP_NO_OPERATION:   { break; } // TODO
        }

        Logger.log("Unimplemented Opcode Type: "+ type);
        return null;
    }
}
