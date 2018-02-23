package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.ValueOperation;
import com.softwareverde.bitcoin.util.ByteUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * NOTE: It seems that all values within Bitcoin Core scripts are stored as little-endian.
 *  To remain consistent with the rest of this library, all values are converted from little-endian
 *  to big-endian for all internal (in-memory) purposes, and then reverted to little-endian when stored.
 *
 * NOTE: All Operation Math and Values appear to be injected into the script as 4-byte integers.
 */
public class ScriptRunner {
    public Boolean runScript(final Script lockingScript, final Script unlockingScript) {
        lockingScript.resetPosition();
        unlockingScript.resetPosition();

        final List<Operation> stack = new ArrayList<Operation>();

        while (lockingScript.hasNextByte()) {
            final Operation opcode = Operation.fromScript(lockingScript);
            stack.add(opcode);
        }

        while (unlockingScript.hasNextByte()) {
            final Operation opcode = Operation.fromScript(unlockingScript);
            stack.add(opcode);
        }

        while (! stack.isEmpty()) {
            final Operation operation = stack.remove(stack.size() - 1);
            operation.applyTo(stack);
        }

        if (stack.isEmpty()) { return false; }

        final Operation topOperationWithinStack = stack.get(stack.size() - 1);
        if (! (topOperationWithinStack instanceof ValueOperation)) { return false; }

        final ValueOperation valueOperation = ((ValueOperation) topOperationWithinStack);
        return (ByteUtil.bytesToLong(valueOperation.getValue()) > 0);
    }
}
