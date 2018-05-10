package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.Bip65;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class LockTimeOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_LOCK_TIME;

    protected static LockTimeOperation fromBytes(final ByteArrayReader byteArrayReader) {
        if (! byteArrayReader.hasBytes()) { return null; }

        final byte opcodeByte = byteArrayReader.readByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final Opcode opcode = TYPE.getSubtype(opcodeByte);
        if (opcode == null) { return null; }

        return new LockTimeOperation(opcodeByte, opcode);
    }

    protected LockTimeOperation(final byte value, final Opcode opcode) {
        super(value, TYPE, opcode);
    }

    @Override
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableContext context) {
        switch (_opcode) {
            case CHECK_LOCK_TIME_THEN_VERIFY: {
                final Boolean operationIsEnabled = Bip65.isEnabled(context.getBlockHeight());
                if (! operationIsEnabled) {
                    // Before Bip65, CHECK_LOCK_TIME_THEN_VERIFY performed as a no-operation.
                    return true;
                }

                final Transaction transaction = context.getTransaction();
                final LockTime transactionLockTime = transaction.getLockTime();

                final TransactionInput transactionInput = context.getTransactionInput();
                final Long transactionInputSequenceNumber = transactionInput.getSequenceNumber();
                if (TransactionInput.MAX_SEQUENCE_NUMBER.equals(transactionInputSequenceNumber)) {
                    return false;
                }

                final Value requiredLockTimeValue = stack.pop();
                final Long requiredLockTimeLong = requiredLockTimeValue.asLong();
                if (requiredLockTimeLong < 0) { return false; }
                final LockTime requiredLockTime = new ImmutableLockTime(requiredLockTimeLong);

                final Boolean lockTimeIsSatisfied;
                {
                    if (requiredLockTime.getType() != transactionLockTime.getType()) {
                        lockTimeIsSatisfied = false;
                    }
                    else {
                        lockTimeIsSatisfied = (transactionLockTime.getValue() >= requiredLockTime.getValue());
                    }
                }

                if (! lockTimeIsSatisfied) {
                    return false;
                }

                return (! stack.didOverflow());
            }

            case CHECK_SEQUENCE_NUMBER_THEN_VERIFY: {
                final Value requiredSequenceNumberValue = stack.pop();
                final Long requiredSequenceNumberLong = requiredSequenceNumberValue.asLong();

                final TransactionInput transactionInput = context.getTransactionInput();
                final Long transactionInputSequenceNumber = transactionInput.getSequenceNumber();
                if (transactionInputSequenceNumber < requiredSequenceNumberLong) {
                    return false;
                }

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
