package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.opcode.controlstate.CodeBlock;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.constable.Const;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.*;

public abstract class Operation implements Const {
    public static class ScriptOperationExecutionException extends Exception { }

    public enum Type {
        OP_PUSH         (PUSH_NEGATIVE_ONE, PUSH_ZERO, PUSH_VALUE, PUSH_DATA, PUSH_DATA_BYTE, PUSH_DATA_SHORT, PUSH_DATA_INTEGER, PUSH_VERSION),
        OP_DYNAMIC_VALUE(PUSH_STACK_SIZE, COPY_1ST, COPY_NTH, COPY_2ND, COPY_2ND_THEN_1ST, COPY_3RD_THEN_2ND_THEN_1ST, COPY_4TH_THEN_3RD, COPY_1ST_THEN_MOVE_TO_3RD),
        OP_CONTROL      (IF, NOT_IF, ELSE, END_IF, VERIFY, RETURN, IF_VERSION, IF_NOT_VERSION),
        OP_STACK        (POP_TO_ALT_STACK, POP_FROM_ALT_STACK, IF_1ST_TRUE_THEN_COPY_1ST, POP, REMOVE_2ND_FROM_TOP, MOVE_NTH_TO_1ST, ROTATE_TOP_3, SWAP_1ST_WITH_2ND, POP_THEN_POP, MOVE_5TH_AND_6TH_TO_TOP, SWAP_1ST_2ND_WITH_3RD_4TH),
        OP_STRING       (STRING_CONCATENATE, STRING_SUBSTRING, STRING_LEFT, STRING_RIGHT, STRING_PUSH_LENGTH),
        OP_BITWISE      (BITWISE_INVERT, BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT),
        OP_COMPARISON   (INTEGER_AND, INTEGER_OR, IS_EQUAL, IS_EQUAL_THEN_VERIFY, IS_TRUE, IS_NUMERICALLY_EQUAL, IS_NUMERICALLY_EQUAL_THEN_VERIFY, IS_NUMERICALLY_NOT_EQUAL, IS_LESS_THAN, IS_GREATER_THAN, IS_LESS_THAN_OR_EQUAL, IS_GREATER_THAN_OR_EQUAL, IS_WITHIN_RANGE),
        OP_ARITHMETIC   (ADD_ONE, SUBTRACT_ONE, MULTIPLY_BY_TWO, DIVIDE_BY_TWO, NEGATE, ABSOLUTE_VALUE, NOT, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULUS, MIN, MAX),
        OP_CRYPTOGRAPHIC(RIPEMD_160, SHA_1, SHA_256, SHA_256_THEN_RIPEMD_160, DOUBLE_SHA_256, CODE_SEPARATOR, CHECK_SIGNATURE, CHECK_SIGNATURE_THEN_VERIFY, CHECK_MULTISIGNATURE, CHECK_MULTISIGNATURE_THEN_VERIFY),
        OP_LOCK_TIME    (CHECK_LOCK_TIME_THEN_VERIFY, CHECK_SEQUENCE_NUMBER_THEN_VERIFY),
        OP_NOTHING      (NO_OPERATION, NO_OPERATION_1, NO_OPERATION_2, RESERVED, RESERVED_1)
        ; // END ENUMS

        public static Type getType(final byte typeByte) {
            for (final Type type : Type.values()) {
                for (final Opcode opcode : type._opcodes) {
                    if (opcode.matchesByte(typeByte)) { return type; }
                }
            }
            return null;
        }

        private final Opcode[] _opcodes;
        Type(final Opcode... opcodes) {
            _opcodes = Util.copyArray(opcodes);
        }

        public List<Opcode> getSubtypes() {
            final List<Opcode> opcodes = new ArrayList<Opcode>(_opcodes.length);
            for (final Opcode opcode : _opcodes) {
                opcodes.add(opcode);
            }
            return opcodes;
        }

        public Opcode getSubtype(final byte b) {
            for (final Opcode opcode : _opcodes) {
                if (opcode.matchesByte(b)) { return opcode; }
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

    public abstract Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) throws ScriptOperationExecutionException;

    public Boolean shouldExecute(final Stack stack, final ControlState controlState, final Context context) {
        if (controlState.isInCodeBlock()) {
            final CodeBlock codeBlock = controlState.getCodeBlock();
            return codeBlock.condition;
        }

        return true;
    }

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

    public String toStandardString() {
        return "0x" + HexUtil.toHexString(new byte[] { _opcodeByte } );
    }
}
