package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.io.Logger;

/**
 * NOTE: It seems that all values within Bitcoin Core scripts are stored as little-endian.
 *  To remain consistent with the rest of this library, all values are converted from little-endian
 *  to big-endian for all internal (in-memory) purposes, and then reverted to little-endian when stored.
 *
 * NOTE: All Operation Math and Values appear to be injected into the script as 4-byte integers.
 */
public class ScriptRunner {
    public Boolean runScript(final Script lockingScript, final Script unlockingScript, final Context context) {
        lockingScript.resetPosition();
        unlockingScript.resetPosition();

        final Stack stack = new Stack();

        try {
            while (unlockingScript.hasNextByte()) {
                final Operation opcode = Operation.fromScript(unlockingScript); // TODO: Change to inflater...
                if (opcode == null) { return false; }

                final Boolean wasSuccessful = opcode.applyTo(stack, context);
                if (! wasSuccessful) { return false; }
            }

            while (lockingScript.hasNextByte()) {
                final Operation opcode = Operation.fromScript(lockingScript); // TODO: Change to inflater...
                if (opcode == null) { return false; }

                final Boolean wasSuccessful = opcode.applyTo(stack, context);
                if (! wasSuccessful) { return false; }
            }
        }
        catch (final Operation.ScriptOperationExecutionException exception) {
            Logger.log(exception);
            return false;
        }

        if (stack.isEmpty()) { return false; }
        return (stack.pop().asBoolean());
    }
}
