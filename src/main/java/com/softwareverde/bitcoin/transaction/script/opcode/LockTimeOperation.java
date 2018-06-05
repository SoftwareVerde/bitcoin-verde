package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.Bip65;
import com.softwareverde.bitcoin.bip.Bip68;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
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
                final SequenceNumber transactionInputSequenceNumber = transactionInput.getSequenceNumber();
                if (SequenceNumber.MAX_SEQUENCE_NUMBER.equals(transactionInputSequenceNumber)) {
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
                final Transaction transaction = context.getTransaction();
                final TransactionInput transactionInput = context.getTransactionInput();

                final Boolean operationIsEnabled = Bip68.isEnabled(transaction);
                if (! operationIsEnabled) {
                    // Before Bip68, CHECK_SEQUENCE_NUMBER_THEN_VERIFY performed as a no-operation.
                    return true;
                }

                // CheckSequenceVerify fails if...
                // the stack is empty; or
                // the top item on the stack is less than 0; or
                // the top item on the stack has the disable flag (1 << 31) unset; and {
                //  the transaction version is less than 2; or
                //  the transaction input sequence number disable flag (1 << 31) is set; or
                //  the relative lock-time type is not the same; or
                //  the top stack item is greater than the transaction sequence (when masked according to the BIP68);
                // }

                final Value stackSequenceNumberValue = stack.pop();
                final LockTime stackSequenceNumber = stackSequenceNumberValue.asLockTime();

                // if (stackSequenceNumber.getValue() < 0L) { return false; } // This check doesn't make the most sense, since the disabled-flag is what will render the value negative... It's possible that Bitcoin Core allows values that are minimum-length encoded.

                if (! stackSequenceNumber.isDisabled()) {
                    if (transaction.getVersion() < 2) {
                        return false;
                    }

                    final SequenceNumber transactionInputSequenceNumber = transactionInput.getSequenceNumber();
                    if (transactionInputSequenceNumber.isDisabled()) {
                        return false;
                    }

                    final LockTime transactionLockTime = transaction.getLockTime();
                    if (transactionLockTime.getType() != transactionInputSequenceNumber.getType()) {
                        return false;
                    }

                    if (stackSequenceNumber.getMaskedValue() > transactionLockTime.getMaskedValue()) {
                        return false;
                    }
                }

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
