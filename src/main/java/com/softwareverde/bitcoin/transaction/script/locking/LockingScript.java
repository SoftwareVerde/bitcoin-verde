package com.softwareverde.bitcoin.transaction.script.locking;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptType;

public interface LockingScript extends Script {
    Integer MAX_SPENDABLE_SCRIPT_BYTE_COUNT = 10000; // The number of bytes allowed in a LockingScript before the script becomes unspendable.

    LockingScript EMPTY_SCRIPT = new ImmutableLockingScript();

    static LockingScript castFrom(final Script script) {
        return new ImmutableLockingScript(script);
    }

    ScriptType getScriptType();

    @Override
    ImmutableLockingScript asConst();
}
