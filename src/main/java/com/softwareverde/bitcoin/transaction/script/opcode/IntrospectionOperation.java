package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class IntrospectionOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_INTROSPECTION;

    public static final IntrospectionOperation PUSH_INPUT_INDEX                         = new IntrospectionOperation(Opcode.PUSH_INPUT_INDEX);
    public static final IntrospectionOperation PUSH_ACTIVE_BYTECODE                     = new IntrospectionOperation(Opcode.PUSH_ACTIVE_BYTECODE);
    public static final IntrospectionOperation PUSH_TRANSACTION_VERSION                 = new IntrospectionOperation(Opcode.PUSH_TRANSACTION_VERSION);
    public static final IntrospectionOperation PUSH_TRANSACTION_INPUT_COUNT             = new IntrospectionOperation(Opcode.PUSH_TRANSACTION_INPUT_COUNT);
    public static final IntrospectionOperation PUSH_TRANSACTION_OUTPUT_COUNT            = new IntrospectionOperation(Opcode.PUSH_TRANSACTION_OUTPUT_COUNT);
    public static final IntrospectionOperation PUSH_TRANSACTION_LOCK_TIME               = new IntrospectionOperation(Opcode.PUSH_TRANSACTION_LOCK_TIME);
    public static final IntrospectionOperation PUSH_PREVIOUS_OUTPUT_VALUE               = new IntrospectionOperation(Opcode.PUSH_PREVIOUS_OUTPUT_VALUE);
    public static final IntrospectionOperation PUSH_PREVIOUS_OUTPUT_BYTECODE            = new IntrospectionOperation(Opcode.PUSH_PREVIOUS_OUTPUT_BYTECODE);
    public static final IntrospectionOperation PUSH_PREVIOUS_OUTPUT_TRANSACTION_HASH    = new IntrospectionOperation(Opcode.PUSH_PREVIOUS_OUTPUT_TRANSACTION_HASH);
    public static final IntrospectionOperation PUSH_PREVIOUS_OUTPUT_INDEX               = new IntrospectionOperation(Opcode.PUSH_PREVIOUS_OUTPUT_INDEX);
    public static final IntrospectionOperation PUSH_INPUT_BYTECODE                      = new IntrospectionOperation(Opcode.PUSH_INPUT_BYTECODE);
    public static final IntrospectionOperation PUSH_INPUT_SEQUENCE_NUMBER               = new IntrospectionOperation(Opcode.PUSH_INPUT_SEQUENCE_NUMBER);
    public static final IntrospectionOperation PUSH_OUTPUT_VALUE                        = new IntrospectionOperation(Opcode.PUSH_OUTPUT_VALUE);
    public static final IntrospectionOperation PUSH_OUTPUT_BYTECODE                     = new IntrospectionOperation(Opcode.PUSH_OUTPUT_BYTECODE);

    protected static IntrospectionOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new IntrospectionOperation(opcode);
    }

    protected Integer _popIntegerValue(final Stack stack, final MutableTransactionContext context) {
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();

        final Value value = stack.pop();
        if (stack.didOverflow()) { return null; }
        if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
            if (! value.isMinimallyEncoded()) { return null; }
        }
        if (! value.isWithinIntegerRange()) { return null; }

        return value.asInteger();
    }

    protected Long _popLongValue(final Stack stack, final MutableTransactionContext context) {
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();

        final Value value = stack.pop();
        if (stack.didOverflow()) { return null; }
        if (upgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)) {
            if (! value.isMinimallyEncoded()) { return null; }
        }
        if (! value.isWithinLongIntegerRange()) { return null; }
        if (! upgradeSchedule.are64BitScriptIntegersEnabled(medianBlockTime)) {
            if (! value.isWithinIntegerRange()) { return null; }
        }

        return value.asLong();
    }

    protected IntrospectionOperation(final Opcode opcode) {
        super(opcode.getValue(), TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        if (! upgradeSchedule.areIntrospectionOperationsEnabled(medianBlockTime)) { return true; } // Opcodes were NO-OPs prior to upgrade...

        switch (_opcode) {
            case PUSH_INPUT_INDEX: {
                final Integer transactionInputIndex = context.getTransactionInputIndex();
                final Value value = Value.fromInteger(transactionInputIndex);
                stack.push(value);
                return true;
            }

            case PUSH_ACTIVE_BYTECODE: {
                final Script script;
                {
                    final Script currentScript = context.getCurrentScript();
                    final Integer lastCodeSeparatorIndex = context.getScriptLastCodeSeparatorIndex();
                    script = (lastCodeSeparatorIndex > 0 ? currentScript.getSubscript(lastCodeSeparatorIndex) : currentScript);
                }

                final Value value = Value.fromBytes(script.getBytes());
                if (value == null) { return false; }

                stack.push(value);
                return true;
            }

            case PUSH_TRANSACTION_VERSION: {
                final Transaction transaction = context.getTransaction();
                final Long transactionVersion = transaction.getVersion();
                final Value value = Value.fromInteger(transactionVersion);

                stack.push(value);
                return true;
            }

            case PUSH_TRANSACTION_INPUT_COUNT: {
                final Transaction transaction = context.getTransaction();
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                final Integer transactionInputCount = transactionInputs.getCount();
                final Value value = Value.fromInteger(transactionInputCount);

                stack.push(value);
                return true;
            }

            case PUSH_TRANSACTION_OUTPUT_COUNT: {
                final Transaction transaction = context.getTransaction();
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                final Integer transactionOutputCount = transactionOutputs.getCount();
                final Value value = Value.fromInteger(transactionOutputCount);

                stack.push(value);
                return true;
            }

            case PUSH_TRANSACTION_LOCK_TIME: {
                final Transaction transaction = context.getTransaction();
                final LockTime lockTime = transaction.getLockTime();
                final Value value = Value.fromInteger(lockTime.getValue());

                stack.push(value);
                return true;
            }

            case PUSH_PREVIOUS_OUTPUT_VALUE: {
                final Integer outputIndex = _popIntegerValue(stack, context);
                if (outputIndex == null) { return false; }
                if (outputIndex < 0) { return false; }

                final TransactionOutput previousOutput = context.getPreviousTransactionOutput(outputIndex);
                if (previousOutput == null) { return false; }

                final Long amount = previousOutput.getAmount();
                final Value value = Value.fromInteger(amount);

                stack.push(value);
                return true;
            }

            case PUSH_PREVIOUS_OUTPUT_BYTECODE: {
                final Integer outputIndex = _popIntegerValue(stack, context);
                if (outputIndex == null) { return false; }
                if (outputIndex < 0) { return false; }

                final TransactionOutput previousOutput = context.getPreviousTransactionOutput(outputIndex);
                if (previousOutput == null) { return false; }

                final Script previousOutputScript = previousOutput.getLockingScript();
                final Value value = Value.fromBytes(previousOutputScript.getBytes());
                if (value == null) { return false; }

                stack.push(value);
                return true;
            }

            case PUSH_PREVIOUS_OUTPUT_TRANSACTION_HASH: {
                final Integer inputIndex = _popIntegerValue(stack, context);
                if (inputIndex == null) { return false; }

                final TransactionInput transactionInput;
                {
                    final Transaction transaction = context.getTransaction();
                    final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                    final int transactionInputCount = transactionInputs.getCount();
                    if (inputIndex >= transactionInputCount) { return false; }
                    if (inputIndex < 0) { return false; }

                    transactionInput = transactionInputs.get(inputIndex);
                }

                final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final Value value = Value.fromBytes(previousTransactionHash.toReverseEndian());

                stack.push(value);
                return true;
            }

            case PUSH_PREVIOUS_OUTPUT_INDEX: {
                final Integer inputIndex = _popIntegerValue(stack, context);
                if (inputIndex == null) { return false; }

                final TransactionInput transactionInput;
                {
                    final Transaction transaction = context.getTransaction();
                    final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                    final int transactionInputCount = transactionInputs.getCount();
                    if (inputIndex >= transactionInputCount) { return false; }
                    if (inputIndex < 0) { return false; }

                    transactionInput = transactionInputs.get(inputIndex);
                }

                final Integer previousOutputIndex = transactionInput.getPreviousOutputIndex();
                final Value value = Value.fromInteger(previousOutputIndex);

                stack.push(value);
                return true;
            }

            case PUSH_INPUT_BYTECODE: {
                final Integer inputIndex = _popIntegerValue(stack, context);
                if (inputIndex == null) { return false; }

                final TransactionInput transactionInput;
                {
                    final Transaction transaction = context.getTransaction();
                    final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                    final int transactionInputCount = transactionInputs.getCount();
                    if (inputIndex >= transactionInputCount) { return false; }
                    if (inputIndex < 0) { return false; }

                    transactionInput = transactionInputs.get(inputIndex);
                }

                final Script unlockingScript = transactionInput.getUnlockingScript();
                final Value value = Value.fromBytes(unlockingScript.getBytes());
                if (value == null) { return false; }

                stack.push(value);
                return true;
            }

            case PUSH_INPUT_SEQUENCE_NUMBER: {
                final Integer inputIndex = _popIntegerValue(stack, context);
                if (inputIndex == null) { return false; }

                final TransactionInput transactionInput;
                {
                    final Transaction transaction = context.getTransaction();
                    final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                    final int transactionInputCount = transactionInputs.getCount();
                    if (inputIndex >= transactionInputCount) { return false; }
                    if (inputIndex < 0) { return false; }

                    transactionInput = transactionInputs.get(inputIndex);
                }

                final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
                final Value value = Value.fromInteger(sequenceNumber.getValue());

                stack.push(value);
                return true;
            }

            case PUSH_OUTPUT_VALUE: {
                final Integer outputIndex = _popIntegerValue(stack, context);
                if (outputIndex == null) { return false; }

                final TransactionOutput transactionOutput;
                {
                    final Transaction transaction = context.getTransaction();
                    final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                    final int transactionOutputCount = transactionOutputs.getCount();
                    if (outputIndex >= transactionOutputCount) { return false; }
                    if (outputIndex < 0) { return false; }

                    transactionOutput = transactionOutputs.get(outputIndex);
                }

                final Long amount = transactionOutput.getAmount();
                final Value value = Value.fromInteger(amount);

                stack.push(value);
                return true;
            }

            case PUSH_OUTPUT_BYTECODE: {
                final Integer outputIndex = _popIntegerValue(stack, context);
                if (outputIndex == null) { return false; }

                final TransactionOutput transactionOutput;
                {
                    final Transaction transaction = context.getTransaction();
                    final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                    final int transactionOutputCount = transactionOutputs.getCount();
                    if (outputIndex >= transactionOutputCount) { return false; }
                    if (outputIndex < 0) { return false; }

                    transactionOutput = transactionOutputs.get(outputIndex);
                }

                final Script lockingScript = transactionOutput.getLockingScript();
                final Value value = Value.fromBytes(lockingScript.getBytes());
                if (value == null) { return false; }

                stack.push(value);
                return true;
            }

            default: { return false; }
        }
    }
}
