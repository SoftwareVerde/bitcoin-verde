package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
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
        final MutableContext mutableContext = new MutableContext(context);

        final Stack stack = new Stack();

        try {
            for (final Operation operation : unlockingScript.getOperations()) {
                final Boolean wasSuccessful = operation.applyTo(stack, mutableContext);
                if (! wasSuccessful) { return false; }
            }

            // NOTE: Resetting the script's position is important to treat the unlocking/locking scripts as separate scripts that share the same stack (vs combining the scripts and treating them as one).
            //  More importantly, this call is necessary to correctly determine the bytes signed during signature-checking operations. (i.e. CODE_SEPARATOR)
            mutableContext.resetScriptPosition();

            for (final Operation operation : lockingScript.getOperations()) {
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
