package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;

public class OperationInflater {
    public Operation fromScriptReader(final ScriptReader scriptReader) {
        if (! scriptReader.hasNextByte()) { return null; }

        final Operation.Type type = Operation.Type.getType(scriptReader.peakNextByte());
        if (type == null) { return null; }

        switch (type) {
            case OP_PUSH: { return PushOperation.fromScriptReader(scriptReader); }
            case OP_DYNAMIC_VALUE: { return DynamicValueOperation.fromScriptReader(scriptReader); }
            case OP_CONTROL:
            case OP_STACK:
            case OP_STRING:
            case OP_BITWISE:
            case OP_COMPARISON: { return ComparisonOperation.fromScriptReader(scriptReader); }
            case OP_ARITHMETIC:
            case OP_CRYPTOGRAPHIC: { return CryptographicOperation.fromScriptReader(scriptReader); }
            case OP_LOCK_TIME:
            case OP_NO_OPERATION:

            default: return null;
        }
    }
}
