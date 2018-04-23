package com.softwareverde.bitcoin.transaction.script.unlocking;

import com.softwareverde.bitcoin.transaction.script.Script;

public interface UnlockingScript extends Script {
    UnlockingScript EMPTY_SCRIPT = new ImmutableUnlockingScript();

    static UnlockingScript castFrom(final Script script) {
        return new ImmutableUnlockingScript(script);
    }

    @Override
    ImmutableUnlockingScript asConst();
}
