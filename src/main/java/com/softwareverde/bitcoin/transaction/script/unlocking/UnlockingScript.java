package com.softwareverde.bitcoin.transaction.script.unlocking;

import com.softwareverde.bitcoin.transaction.script.Script;

public interface UnlockingScript extends Script {
    UnlockingScript EMPTY_SCRIPT = new ImmutableUnlockingScript(new byte[0]);

    static UnlockingScript castFrom(final Script script) {
        return new ImmutableUnlockingScript(script.getBytes());
    }

    @Override
    ImmutableUnlockingScript asConst();
}
