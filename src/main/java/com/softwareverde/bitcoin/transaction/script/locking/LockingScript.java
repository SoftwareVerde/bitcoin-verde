package com.softwareverde.bitcoin.transaction.script.locking;

import com.softwareverde.bitcoin.transaction.script.Script;

public interface LockingScript extends Script {
    LockingScript EMPTY_SCRIPT = new ImmutableLockingScript();

    static LockingScript castFrom(final Script script) {
        return new ImmutableLockingScript(script);
    }

    @Override
    ImmutableLockingScript asConst();
}
