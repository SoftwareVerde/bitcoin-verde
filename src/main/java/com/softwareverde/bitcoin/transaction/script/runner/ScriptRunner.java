package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.OperationInflater;
import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

/**
 * NOTE: It seems that all values within Bitcoin Core scripts are stored as little-endian.
 *  To remain consistent with the rest of this library, all values are converted from little-endian
 *  to big-endian for all internal (in-memory) purposes, and then reverted to little-endian when stored.
 *
 * NOTE: All Operation Math and Values appear to be injected into the script as 4-byte integers.
 */
public class ScriptRunner {
    public Boolean runScript(final Script lockingScript, final Script unlockingScript, final Context context) {
        final ScriptReader lockingScriptReader = new ScriptReader(lockingScript);
        final ScriptReader unlockingScriptReader = new ScriptReader(unlockingScript);
        final OperationInflater operationInflater = new OperationInflater();

        final Stack stack = new Stack();

        try {
            while (unlockingScriptReader.hasNextByte()) {
                final Operation opcode = operationInflater.fromScriptReader(unlockingScriptReader);
                if (opcode == null) { return false; }

                final Boolean wasSuccessful = opcode.applyTo(stack, context);
                if (! wasSuccessful) { return false; }
            }

            while (lockingScriptReader.hasNextByte()) {
                final Operation opcode = operationInflater.fromScriptReader(lockingScriptReader);
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

    /**
     * Returns the index within script that starts with the index immediately after the last executed CODE_SEPARATOR operation.
     * Ex: Given:
     *      ix:         0     | 1     | 2              | 3
     *      opcodes:    NO_OP | NO_OP | CODE_SEPARATOR | NO_OP
     *  ScriptRunner.getCodeSeparatorIndex() will return: 3
     *  // TODO:
     *  //  The script is executed in order to handle for if-else processing.  However, these control structures have
     *  //  not been implemented at the time of implementing this function.  Therefore, changes will likely need to be made.
     */
    public Integer getCodeSeparatorIndex(final Script script) {
        final OperationInflater operationInflater = new OperationInflater();
        final ScriptReader scriptReader = new ScriptReader(script);

        int lastCodeSeparatorIndex = 0;
        while (scriptReader.hasNextByte()) {
            final Operation opcode = operationInflater.fromScriptReader(scriptReader);
            if (opcode == null) { return null; }

            final byte opcodeByte = opcode.getOpcodeByte();
            Logger.log(HexUtil.toHexString(new byte[]{ opcodeByte }));
            final boolean isCodeSeparator = Operation.SubType.CODE_SEPARATOR.matchesByte(opcodeByte);
            if (isCodeSeparator) {
                Logger.log("Position: "+ scriptReader.getPosition());
                lastCodeSeparatorIndex = scriptReader.getPosition();
            }
        }

        // 76 A9 14 6C 45 6D A0 F9 AB E7 F9 57 28 43 A6 D9 44 0C 23 C1 42 1A CA 88 AC

        Logger.log(script);
        Logger.log("Last Code Separator Index: "+ lastCodeSeparatorIndex);
        return lastCodeSeparatorIndex;
    }
}
