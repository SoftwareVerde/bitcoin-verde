package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.constable.list.List;
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
        final MutableContext mutableContext = new MutableContext(context);

        final Stack stack = new Stack();

        try {
            final List<Operation> unlockingScriptOperations = unlockingScript.getOperations();
            if (unlockingScriptOperations == null) { return false; }

            mutableContext.setCurrentScript(unlockingScript);
            for (final Operation operation : unlockingScriptOperations) {
                final Boolean wasSuccessful = operation.applyTo(stack, mutableContext);
                if (! wasSuccessful) { return false; }
            }

            final List<Operation> lockingScriptOperations = lockingScript.getOperations();
            if (lockingScriptOperations == null) { return false; }

            mutableContext.setCurrentScript(lockingScript);
            for (final Operation operation : lockingScriptOperations) {
                final Boolean wasSuccessful = operation.applyTo(stack, mutableContext);
                if (! wasSuccessful) { return false; }
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return false;
        }

        if (stack.isEmpty()) { return false; }
        return (stack.pop().asBoolean());
    }
}
