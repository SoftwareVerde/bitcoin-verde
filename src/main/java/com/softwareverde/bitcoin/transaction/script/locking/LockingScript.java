package com.softwareverde.bitcoin.transaction.script.locking;

import com.softwareverde.bitcoin.transaction.script.Script;

public interface LockingScript extends Script {
    LockingScript EMPTY_SCRIPT = new ImmutableLockingScript(new byte[0]);

    @Override
    ImmutableLockingScript asConst();
}
