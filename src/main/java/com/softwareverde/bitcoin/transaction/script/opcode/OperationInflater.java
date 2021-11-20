package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class OperationInflater {
    public Operation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte b = byteArrayReader.peakByte();
        final Operation.Type type = Operation.Type.getType(b);
        if (type == null) {
            Logger.debug("Unknown Operation Type: 0x"+ HexUtil.toHexString(new byte[]{ b }));
            return null;
        }

        final Integer originalPosition = byteArrayReader.getPosition();

        final Operation operation;
        switch (type) {
            case OP_PUSH:           { operation = PushOperation.fromBytes(byteArrayReader); }                       break;
            case OP_DYNAMIC_VALUE:  { operation = DynamicValueOperation.fromBytes(byteArrayReader); }               break;
            case OP_INTROSPECTION:  { operation = IntrospectionOperation.fromBytes(byteArrayReader); }              break;
            case OP_CONTROL:        { operation = ControlOperation.fromBytes(byteArrayReader); }                    break;
            case OP_STACK:          { operation = StackOperation.fromBytes(byteArrayReader); }                      break;
            case OP_STRING:         { operation = StringOperation.fromBytes(byteArrayReader); }                     break;
            case OP_COMPARISON:     { operation = ComparisonOperation.fromBytes(byteArrayReader); }                 break;
            case OP_ARITHMETIC:     { operation = ArithmeticOperation.fromBytes(byteArrayReader); }                 break;
            case OP_CRYPTOGRAPHIC:  { operation = CryptographicOperation.fromBytes(byteArrayReader); }              break;
            case OP_LOCK_TIME:      { operation = LockTimeOperation.fromBytes(byteArrayReader); }                   break;
            case OP_NOTHING:        { operation = NothingOperation.fromBytes(byteArrayReader); }                    break;
            case OP_INVALID:        { operation = InvalidOperation.fromBytes(byteArrayReader, false); }  break;
            case OP_BITWISE:        { operation = BitwiseOperation.fromBytes(byteArrayReader); }                    break;
            default: {
                Logger.debug("Unimplemented Opcode Type: "+ type + " (0x" + HexUtil.toHexString(new byte[] { b }) + ")");
                operation = null;
            }
        }

        if (operation != null) {
            return operation;
        }

        final boolean failIfPresent = (type == Operation.Type.OP_PUSH);

        byteArrayReader.setPosition(originalPosition);
        return InvalidOperation.fromBytes(byteArrayReader, failIfPresent);
    }

    public Operation fromBytes(final ByteArray byteArray) {
        return fromBytes(new ByteArrayReader(byteArray));
    }
}
