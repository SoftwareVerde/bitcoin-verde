package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
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
    public Boolean applyTo(final Stack stack, final ControlState controlState, final MutableTransactionContext context) {
        final UpgradeSchedule upgradeSchedule = context.getUpgradeSchedule();
        final Long blockHeight = context.getBlockHeight();

        switch (_opcode) {
            case CHECK_LOCK_TIME_THEN_VERIFY: {
                final Boolean operationIsEnabled = upgradeSchedule.isCheckLockTimeOperationEnabled(blockHeight);
                if (! operationIsEnabled) {
                    return true; // NOTE: Before Bip65, CHECK_LOCK_TIME_THEN_VERIFY performed as a NOP...
                }

                // CheckLockTimeThenVerify fails if...
                // the stack is empty; or
                // the top item on the stack is less than 0; or
                // the lock-time type (height vs. timestamp) of the top stack item and the nLockTime field are not the same; or
                // the top stack item is greater than the transaction's nLockTime field; or
                // the nSequence field of the txin is 0xffffffff

                final Transaction transaction = context.getTransaction();
                final LockTime transactionLockTime = transaction.getLockTime();

                final Value requiredLockTimeValue = stack.peak();
                if (requiredLockTimeValue.asLong() < 0L) { return false; } // NOTE: This is possible since 5-bytes are permitted when parsing Lock/SequenceNumbers...

                final LockTime stackLockTime = requiredLockTimeValue.asLockTime();

                if (stackLockTime.getType() != transactionLockTime.getType()) { return false; }
                if (stackLockTime.getValue() > transactionLockTime.getValue()) { return false; }

                final TransactionInput transactionInput = context.getTransactionInput();
                final SequenceNumber transactionInputSequenceNumber = transactionInput.getSequenceNumber();
                if (SequenceNumber.MAX_SEQUENCE_NUMBER.equals(transactionInputSequenceNumber)) {
                    return false;
                }

                return (! stack.didOverflow());
            }

            case CHECK_SEQUENCE_NUMBER_THEN_VERIFY: {
                final Boolean operationIsEnabled = (upgradeSchedule.isCheckSequenceNumberOperationEnabled(blockHeight));
                if (! operationIsEnabled) {
                    return true; // NOTE: Before Bip112, the operation is considered a NOP...
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

                final Transaction transaction = context.getTransaction();
                final TransactionInput transactionInput = context.getTransactionInput();

                final Value stackSequenceNumberValue = stack.peak();
                if (stackSequenceNumberValue.asLong() < 0L) { return false; } // NOTE: This is possible due to the value's weird encoding rules and/or also possible since 5-bytes are permitted when parsing SequenceNumbers...

                final SequenceNumber stackSequenceNumber = stackSequenceNumberValue.asSequenceNumber();

                if (! stackSequenceNumber.isRelativeLockTimeDisabled()) {
                    if (transaction.getVersion() < 2) {
                        return false;
                    }

                    final SequenceNumber transactionInputSequenceNumber = transactionInput.getSequenceNumber();
                    if (transactionInputSequenceNumber.isRelativeLockTimeDisabled()) {
                        return false;
                    }

                    if (stackSequenceNumber.getType() != transactionInputSequenceNumber.getType()) {
                        return false;
                    }

                    if (stackSequenceNumber.getMaskedValue() > transactionInputSequenceNumber.getMaskedValue()) {
                        return false;
                    }
                }

                return (! stack.didOverflow());
            }

            default: { return false; }
        }
    }
}
