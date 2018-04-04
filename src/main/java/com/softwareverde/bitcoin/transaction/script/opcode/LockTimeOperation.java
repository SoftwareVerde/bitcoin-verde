package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.io.Logger;

public class LockTimeOperation extends SubTypedOperation {
    public static final Type TYPE = Type.OP_LOCK_TIME;

    protected static LockTimeOperation fromScriptReader(final ScriptReader scriptReader) {
        if (! scriptReader.hasNextByte()) { return null; }

        final byte opcodeByte = scriptReader.getNextByte();
        final Type type = Type.getType(opcodeByte);
        if (type != TYPE) { return null; }

        final SubType subType = TYPE.getSubtype(opcodeByte);
        if (subType == null) { return null; }

        return new LockTimeOperation(opcodeByte, subType);
    }

    protected LockTimeOperation(final byte value, final SubType subType) {
        super(value, TYPE, subType);
    }

    @Override
    public Boolean applyTo(final Stack stack, final Context context) {
        switch (_subType) {
            case CHECK_LOCK_TIME_THEN_VERIFY: {
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

                if (! lockTimeIsSatisfied) { return false; }

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
