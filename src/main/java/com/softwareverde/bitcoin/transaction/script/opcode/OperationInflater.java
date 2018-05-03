package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class OperationInflater {
    public Operation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte b = byteArrayReader.peakByte();
        final Operation.Type type = Operation.Type.getType(b);
        if (type == null) {
            Logger.log("Unknown Operation Type: 0x"+ HexUtil.toHexString(new byte[]{ b }));
            return null;
        }

        switch (type) {
            case OP_PUSH:           { return PushOperation.fromBytes(byteArrayReader); }
            case OP_DYNAMIC_VALUE:  { return DynamicValueOperation.fromBytes(byteArrayReader); }
            case OP_CONTROL:        { break; } // TODO
            case OP_STACK:          { return StackOperation.fromBytes(byteArrayReader); }
            case OP_STRING:         { return StringOperation.fromBytes(byteArrayReader); }
            case OP_BITWISE:        { break; } // TODO
            case OP_COMPARISON:     { return ComparisonOperation.fromBytes(byteArrayReader); }
            case OP_ARITHMETIC:     { return ArithmeticOperation.fromBytes(byteArrayReader); }
            case OP_CRYPTOGRAPHIC:  { return CryptographicOperation.fromBytes(byteArrayReader); }
            case OP_LOCK_TIME:      { return LockTimeOperation.fromBytes(byteArrayReader); }
            case OP_NOTHING:        { return NothingOperation.fromBytes(byteArrayReader); }
        }

        Logger.log("Unimplemented Opcode Type: "+ type + " (0x" + HexUtil.toHexString(new byte[] { b }) + ")");
        return null;
    }
}
