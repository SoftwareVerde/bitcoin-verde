package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.script.opcode.controlstate.CodeBlock;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.runner.context.TransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.ABSOLUTE_VALUE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.ADD;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.ADD_ONE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.BITWISE_AND;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.BITWISE_INVERT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.BITWISE_OR;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.BITWISE_XOR;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_DATA_SIGNATURE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_DATA_SIGNATURE_THEN_VERIFY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_LOCK_TIME_THEN_VERIFY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_MULTISIGNATURE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_MULTISIGNATURE_THEN_VERIFY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_SEQUENCE_NUMBER_THEN_VERIFY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_SIGNATURE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CHECK_SIGNATURE_THEN_VERIFY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CODE_SEPARATOR;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.CONCATENATE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.COPY_1ST;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.COPY_1ST_THEN_MOVE_TO_3RD;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.COPY_2ND;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.COPY_2ND_THEN_1ST;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.COPY_3RD_THEN_2ND_THEN_1ST;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.COPY_4TH_THEN_3RD;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.COPY_NTH;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.DIVIDE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.DIVIDE_BY_TWO;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.DOUBLE_SHA_256;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.ELSE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.ENCODE_NUMBER;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.END_IF;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IF;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IF_1ST_TRUE_THEN_COPY_1ST;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IF_NOT_VERSION;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IF_VERSION;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.INTEGER_AND;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.INTEGER_OR;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_EQUAL;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_EQUAL_THEN_VERIFY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_GREATER_THAN;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_GREATER_THAN_OR_EQUAL;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_LESS_THAN;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_LESS_THAN_OR_EQUAL;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_NUMERICALLY_EQUAL;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_NUMERICALLY_EQUAL_THEN_VERIFY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_NUMERICALLY_NOT_EQUAL;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_TRUE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.IS_WITHIN_RANGE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.MAX;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.MIN;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.MODULUS;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.MOVE_5TH_AND_6TH_TO_TOP;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.MOVE_NTH_TO_1ST;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.MULTIPLY;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.MULTIPLY_BY_TWO;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.NEGATE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.NOT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.NOT_IF;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.NO_OPERATION;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.NO_OPERATION_1;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.NO_OPERATION_2;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.NUMBER_TO_BYTES;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.POP;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.POP_FROM_ALT_STACK;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.POP_THEN_POP;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.POP_TO_ALT_STACK;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_1ST_BYTE_COUNT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_DATA;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_DATA_BYTE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_DATA_INTEGER;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_DATA_SHORT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_NEGATIVE_ONE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_STACK_SIZE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_VALUE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_VERSION;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.PUSH_ZERO;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.REMOVE_2ND_FROM_TOP;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.RESERVED;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.RESERVED_1;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.RETURN;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.REVERSE_BYTES;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.RIPEMD_160;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.ROTATE_TOP_3;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SHA_1;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SHA_256;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SHA_256_THEN_RIPEMD_160;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SHIFT_LEFT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SHIFT_RIGHT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SPLIT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SUBTRACT;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SUBTRACT_ONE;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SWAP_1ST_2ND_WITH_3RD_4TH;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.SWAP_1ST_WITH_2ND;
import static com.softwareverde.bitcoin.transaction.script.opcode.Opcode.VERIFY;

public abstract class Operation implements Const {
    public static class ScriptOperationExecutionException extends Exception { }

    public enum Type {
        OP_PUSH         (PUSH_NEGATIVE_ONE, PUSH_ZERO, PUSH_VALUE, PUSH_DATA, PUSH_DATA_BYTE, PUSH_DATA_SHORT, PUSH_DATA_INTEGER, PUSH_VERSION),
        OP_DYNAMIC_VALUE(PUSH_STACK_SIZE, COPY_1ST, COPY_NTH, COPY_2ND, COPY_2ND_THEN_1ST, COPY_3RD_THEN_2ND_THEN_1ST, COPY_4TH_THEN_3RD, COPY_1ST_THEN_MOVE_TO_3RD),
        OP_CONTROL      (IF, NOT_IF, ELSE, END_IF, VERIFY, RETURN, IF_VERSION, IF_NOT_VERSION),
        OP_STACK        (POP_TO_ALT_STACK, POP_FROM_ALT_STACK, IF_1ST_TRUE_THEN_COPY_1ST, POP, REMOVE_2ND_FROM_TOP, MOVE_NTH_TO_1ST, ROTATE_TOP_3, SWAP_1ST_WITH_2ND, POP_THEN_POP, MOVE_5TH_AND_6TH_TO_TOP, SWAP_1ST_2ND_WITH_3RD_4TH),
        OP_STRING       (CONCATENATE, SPLIT, ENCODE_NUMBER, NUMBER_TO_BYTES, PUSH_1ST_BYTE_COUNT, REVERSE_BYTES),
        OP_BITWISE      (BITWISE_INVERT, BITWISE_AND, BITWISE_OR, BITWISE_XOR, SHIFT_LEFT, SHIFT_RIGHT),
        OP_COMPARISON   (INTEGER_AND, INTEGER_OR, IS_EQUAL, IS_EQUAL_THEN_VERIFY, IS_TRUE, IS_NUMERICALLY_EQUAL, IS_NUMERICALLY_EQUAL_THEN_VERIFY, IS_NUMERICALLY_NOT_EQUAL, IS_LESS_THAN, IS_GREATER_THAN, IS_LESS_THAN_OR_EQUAL, IS_GREATER_THAN_OR_EQUAL, IS_WITHIN_RANGE),
        OP_ARITHMETIC   (ADD_ONE, SUBTRACT_ONE, MULTIPLY_BY_TWO, DIVIDE_BY_TWO, NEGATE, ABSOLUTE_VALUE, NOT, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULUS, MIN, MAX),
        OP_CRYPTOGRAPHIC(RIPEMD_160, SHA_1, SHA_256, SHA_256_THEN_RIPEMD_160, DOUBLE_SHA_256, CODE_SEPARATOR, CHECK_SIGNATURE, CHECK_SIGNATURE_THEN_VERIFY, CHECK_MULTISIGNATURE, CHECK_MULTISIGNATURE_THEN_VERIFY, CHECK_DATA_SIGNATURE, CHECK_DATA_SIGNATURE_THEN_VERIFY),
        OP_LOCK_TIME    (CHECK_LOCK_TIME_THEN_VERIFY, CHECK_SEQUENCE_NUMBER_THEN_VERIFY),
        OP_NOTHING      (NO_OPERATION, NO_OPERATION_1, NO_OPERATION_2, RESERVED, RESERVED_1),
        OP_INVALID      ()
        ; // END ENUMS

        public static Type getType(final byte typeByte) {
            for (final Type type : Type.values()) {
                for (final Opcode opcode : type._opcodes) {
                    if (opcode.matchesByte(typeByte)) { return type; }
                }
            }

            return OP_INVALID;
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

    protected static Boolean isWithinIntegerRange(final Long value) {
        return ( (value <= Integer.MAX_VALUE) && (value > Integer.MIN_VALUE) ); // MIP-Encoding -2147483648 requires 5 bytes...
    }

    protected static Boolean isMinimallyEncoded(final ByteArray byteArray) {
        final ByteArray minimallyEncodedByteArray = Value.minimallyEncodeBytes(byteArray);
        if (minimallyEncodedByteArray == null) { return false; }

        return (byteArray.getByteCount() == minimallyEncodedByteArray.getByteCount());
    }

    protected static Boolean validateMinimalEncoding(final Value value, final TransactionContext transactionContext) {
        {
            final UpgradeSchedule upgradeSchedule = transactionContext.getUpgradeSchedule();
            final MedianBlockTime medianBlockTime = transactionContext.getMedianBlockTime();
            if (! upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) { return true; }
        }

        return Operation.isMinimallyEncoded(value);
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

    public abstract Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) throws ScriptOperationExecutionException;

    public Boolean shouldExecute(final Stack stack, final ControlState controlState, final TransactionContext transactionContext) {
        if (controlState.isInCodeBlock()) {
            final CodeBlock codeBlock = controlState.getCodeBlock();
            return Util.coalesce(codeBlock.condition, false);
        }

        return true;
    }

    public Boolean failIfPresent() {
        final Opcode opcode = _type.getSubtype(_opcodeByte);
        if (opcode == null) { return false; } // Undefined subtypes are allowed to be present...
        return opcode.failIfPresent();
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
