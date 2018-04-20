package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

import static com.softwareverde.bitcoin.transaction.script.opcode.Operation.SubType.*;

public abstract class Operation {
    public static class ScriptOperationExecutionException extends Exception { }

    public enum SubType {
        // VALUE
        PUSH_NEGATIVE_ONE                   (0x4F),
        PUSH_ZERO                           (0x00),
        PUSH_VALUE                          (0x51, 0x60),
        PUSH_DATA                           (0x01, 0x4B),
        PUSH_DATA_BYTE                      (0x4C),
        PUSH_DATA_SHORT                     (0x4D),
        PUSH_DATA_INTEGER                   (0x4E),

        // DYNAMIC VALUE
        PUSH_STACK_SIZE                     (0x74),
        COPY_1ST                            (0x76),
        COPY_NTH                            (0x79),
        COPY_2ND                            (0x78),
        COPY_2ND_THEN_1ST                   (0x6E),
        COPY_3RD_THEN_2ND_THEN_1ST          (0x6F),
        COPY_4TH_THEN_3RD                   (0x70),
        COPY_1ST_THEN_MOVE_TO_3RD           (0x7D),

        // CONTROL
        IF                                  (0x63),
        NOT_IF                              (0x64),
        ELSE                                (0x67),
        END_IF                              (0x68),
        VERIFY                              (0x69),
        RETURN                              (0x6A),

        // STACK
        POP_TO_ALT_STACK                    (0x6B),
        POP_FROM_ALT_STACK                  (0x6C),
        IF_1ST_TRUE_THEN_COPY_1ST           (0x73),
        POP                                 (0x75),
        REMOVE_2ND_FROM_TOP                 (0x77),
        MOVE_NTH_TO_1ST                     (0x7A),
        ROTATE_TOP_3                        (0x7B),
        SWAP_1ST_WITH_2ND                   (0x7C),
        POP_THEN_POP                        (0x6D),
        MOVE_5TH_AND_6TH_TO_TOP             (0x71),
        SWAP_1ST_2ND_WITH_3RD_4TH           (0x72),

        // STRING
        STRING_CONCATENATE                  (0x7E, false),
        STRING_SUBSTRING                    (0x7F, false),
        STRING_LEFT                         (0x80, false),
        STRING_RIGHT                        (0x81, false),
        STRING_PUSH_LENGTH                  (0x82),
        STRING_1ST_AND_2ND_LENGTH_NOT_EMPTY (0x9A),
        STRING_1ST_OR_2ND_LENGTH_NOT_EMPTY  (0x9B),

        // BITWISE
        BITWISE_INVERT                      (0x83, false),
        BITWISE_AND                         (0x84, false),
        BITWISE_OR                          (0x85, false),
        BITWISE_XOR                         (0x86, false),
        SHIFT_LEFT                          (0x98, false),
        SHIFT_RIGHT                         (0x99, false),

        // COMPARISON
        IS_EQUAL                            (0x87),
        IS_EQUAL_THEN_VERIFY                (0x88),
        IS_FALSE                            (0x92),
        IS_NUMERICALLY_EQUAL                (0x9C),
        IS_NUMERICALLY_EQUAL_THEN_VERIFY    (0x9D),
        IS_NUMERICALLY_NOT_EQUAL            (0x9E),
        IS_LESS_THAN                        (0x9F),
        IS_GREATER_THAN                     (0xA0),
        IS_LESS_THAN_OR_EQUAL               (0xA1),
        IS_GREATER_THAN_OR_EQUAL            (0xA2),
        IS_WITHIN_RANGE                     (0xA5),

        // ARITHMETIC
        ADD_ONE                             (0x8B),
        SUBTRACT_ONE                        (0x8C),
        MULTIPLY_BY_TWO                     (0x8D, false),
        DIVIDE_BY_TWO                       (0x8E, false),
        NEGATE                              (0x8F),
        ABSOLUTE_VALUE                      (0x90),
        NOT                                 (0x91),
        ADD                                 (0x93),
        SUBTRACT                            (0x94),
        MULTIPLY                            (0x95, false),
        DIVIDE                              (0x96, false),
        MODULUS                             (0x97, false),
        MIN                                 (0xA3),
        MAX                                 (0xA4),

        // CRYPTOGRAPHIC
        RIPEMD_160                          (0xA6),
        SHA_1                               (0xA7),
        SHA_256                             (0xA8),
        SHA_256_THEN_RIPEMD_160             (0xA9),
        DOUBLE_SHA_256                      (0xAA),
        CHECK_SIGNATURE                     (0xAC),
        CHECK_SIGNATURE_THEN_VERIFY         (0xAD),
        CHECK_MULTISIGNATURE                (0xAE),
        CHECK_MULTISIGNATURE_THEN_VERIFY    (0xAF),

        CODE_SEPARATOR                      (0xAB), //  CODE_SEPARATOR's intended use was to designate where signed-content is supposed to begin (rendering parts of the script mutable).
                                                    //  Its benefit seems rare and borderline useless, and is likely a security risk.
                                                    //  https://bitcoin.stackexchange.com/questions/34013/what-is-op-codeseparator-used-for

        // LOCK TIME
        CHECK_LOCK_TIME_THEN_VERIFY         (0xB1),
        CHECK_SEQUENCE_NUMBER_THEN_VERIFY   (0xB2),

        // NO OPERATION
        NO_OPERATION                        (0x61),
        NO_OPERATION_1                      (0xB0),
        NO_OPERATION_2                      (0xB3, 0xB9)

        ; // END ENUMS

        private final boolean _isEnabled;
        private final int _minValue;
        private final int _maxValue;

        SubType(final int base) {
            _minValue = base;
            _maxValue = base;
            _isEnabled = true;
        }

        SubType(final int base, final boolean isEnabled) {
            _minValue = base;
            _maxValue = base;
            _isEnabled = isEnabled;
        }

        SubType(final int minValue, final int maxValue) {
            _minValue = minValue;
            _maxValue = maxValue;
            _isEnabled = true;
        }

        SubType(final int minValue, final int maxValue, final boolean isEnabled) {
            _minValue = minValue;
            _maxValue = maxValue;
            _isEnabled = isEnabled;
        }

        public boolean isEnabled() { return _isEnabled; }
        public byte getValue() { return (byte) _minValue; }
        public int getMinValue() { return _minValue; }
        public int getMaxValue() { return _maxValue; }

        public boolean matchesByte(byte b) {
            final int bValue = ByteUtil.byteToInteger(b);
            return (_minValue <= bValue && bValue <= _maxValue);
        }
    }

    public enum Type {
        OP_PUSH         (PUSH_NEGATIVE_ONE, PUSH_ZERO, PUSH_VALUE, PUSH_DATA, PUSH_DATA_BYTE, PUSH_DATA_SHORT, PUSH_DATA_INTEGER),
        OP_DYNAMIC_VALUE(PUSH_STACK_SIZE, COPY_1ST, COPY_NTH, COPY_2ND, COPY_2ND_THEN_1ST, COPY_3RD_THEN_2ND_THEN_1ST, COPY_4TH_THEN_3RD, COPY_1ST_THEN_MOVE_TO_3RD),
        OP_CONTROL      (IF, NOT_IF, ELSE, END_IF, VERIFY, RETURN),
        OP_STACK        (POP_TO_ALT_STACK, POP_FROM_ALT_STACK, IF_1ST_TRUE_THEN_COPY_1ST, POP, REMOVE_2ND_FROM_TOP, MOVE_NTH_TO_1ST, ROTATE_TOP_3, SWAP_1ST_WITH_2ND, POP_THEN_POP, MOVE_5TH_AND_6TH_TO_TOP, SWAP_1ST_2ND_WITH_3RD_4TH),
        OP_STRING       (STRING_CONCATENATE, STRING_SUBSTRING, STRING_LEFT, STRING_RIGHT, STRING_PUSH_LENGTH, STRING_1ST_AND_2ND_LENGTH_NOT_EMPTY, STRING_1ST_OR_2ND_LENGTH_NOT_EMPTY),
        OP_BITWISE      (BITWISE_INVERT, BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT),
        OP_COMPARISON   (IS_EQUAL, IS_EQUAL_THEN_VERIFY, IS_FALSE, IS_NUMERICALLY_EQUAL, IS_NUMERICALLY_EQUAL_THEN_VERIFY, IS_NUMERICALLY_NOT_EQUAL, IS_LESS_THAN, IS_GREATER_THAN, IS_LESS_THAN_OR_EQUAL, IS_GREATER_THAN_OR_EQUAL, IS_WITHIN_RANGE),
        OP_ARITHMETIC   (ADD_ONE, SUBTRACT_ONE, MULTIPLY_BY_TWO, DIVIDE_BY_TWO, NEGATE, ABSOLUTE_VALUE, NOT, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULUS, MIN, MAX),
        OP_CRYPTOGRAPHIC(RIPEMD_160, SHA_1, SHA_256, SHA_256_THEN_RIPEMD_160, DOUBLE_SHA_256, CODE_SEPARATOR, CHECK_SIGNATURE, CHECK_SIGNATURE_THEN_VERIFY, CHECK_MULTISIGNATURE, CHECK_MULTISIGNATURE_THEN_VERIFY),
        OP_LOCK_TIME    (CHECK_LOCK_TIME_THEN_VERIFY, CHECK_SEQUENCE_NUMBER_THEN_VERIFY),
        OP_NOTHING      (NO_OPERATION, NO_OPERATION_1, NO_OPERATION_2)
        ; // END ENUMS

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

    protected final byte _opcodeByte;
    protected final Type _type;

    protected Operation(final byte value, final Type type) {
        _opcodeByte = value;
        _type = type;
    }

    public byte getOpcodeByte() {
        return _opcodeByte;
    }

    public Type getType() {
        return _type;
    }

    public abstract Boolean applyTo(final Stack stack, final MutableContext context) throws ScriptOperationExecutionException;

    public byte[] getBytes() {
        return new byte[] { _opcodeByte };
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof Operation)) { return false ;}
        final Operation operation = (Operation) object;
        if (operation._type != _type) { return false; }
        if (operation._opcodeByte != _opcodeByte) { return false; }

        return true;
    }

    @Override
    public String toString() {
        return "0x" + HexUtil.toHexString(new byte[] { _opcodeByte } ) + " " + _type;
    }
}
