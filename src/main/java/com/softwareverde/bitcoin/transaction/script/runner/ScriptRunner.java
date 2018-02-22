package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;

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

        while (lockingScript.hasNextByte()) {
            final Operation opcode = Operation.fromScript(lockingScript);
        }

        return false;
    }
}
