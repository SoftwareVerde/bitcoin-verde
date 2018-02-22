package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

import static com.softwareverde.bitcoin.transaction.script.opcode.Operation.SubType.*;

public class Operation {
    public enum SubType {
        PUSH_ZERO           (0x00),
        PUSH_DATA           (0x01, 0x4B),
        PUSH_DATA_BYTE      (0x4C),
        PUSH_DATA_SHORT     (0x4D),
        PUSH_DATA_INTEGER   (0x4E),
        PUSH_NEGATIVE_ONE   (0x4F),
        PUSH_VALUE          (0x51, 0x60);

        private final int _minValue;
        private final int _maxValue;

        SubType(final int base) {
            _minValue = base;
            _maxValue = base;
        }

        SubType(final int minValue, final int maxValue) {
            _minValue = minValue;
            _maxValue = maxValue;
        }

        public int getMinValue() { return _minValue; }
        public int getMaxValue() { return _maxValue; }

        public boolean matchesByte(byte b) {
            final int bValue = ByteUtil.byteToInteger(b);
            return (_minValue <= bValue && bValue <= _maxValue);
        }
    }

    public enum Type {
        OP_VALUE (PUSH_ZERO, PUSH_DATA, PUSH_DATA_BYTE, PUSH_DATA_SHORT, PUSH_DATA_INTEGER);

        public static Type getType(final byte typeByte) {
            for (final Type type : Type.values()) {
                for (final SubType subType : type._subTypes) {
                    if (subType.matchesByte(typeByte)) { return type; }
                }
            }
            return null;
        }

        private final SubType[] _subTypes;
        Type(final SubType... subTypes) {
            _subTypes = Util.copyArray(subTypes);
        }

        public List<SubType> getSubtypes() {
            final List<SubType> subTypes = new ArrayList<SubType>();
            for (final SubType subType : _subTypes) {
                subTypes.add(subType);
            }
            return subTypes;
        }

        public SubType getSubtype(final byte b) {
            for (final SubType subType : _subTypes) {
                if (subType.matchesByte(b)) { return subType; }
            }
            return null;
        }
    }

    public static Operation fromScript(final Script script) {
        if (! script.hasNextByte()) { return null; }

        final Type type = Type.getType(script.peakNextByte());
        if (type == null) { return null; }

        switch (type) {
            case OP_VALUE:
            default: return null;
        }
    }

    private final byte _byte;
    private final Type _type;

    protected Operation(final byte value, final Type type) {
        _byte = value;
        _type = type;
    }

    public byte getByte() {
        return _byte;
    }

    public Type getType() {
        return _type;
    }
}
