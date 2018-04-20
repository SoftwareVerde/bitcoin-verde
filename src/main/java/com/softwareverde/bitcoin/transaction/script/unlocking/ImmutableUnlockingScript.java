package com.softwareverde.bitcoin.transaction.script.unlocking;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;

public class ImmutableUnlockingScript extends ImmutableScript implements UnlockingScript {

    protected ImmutableUnlockingScript() {
        super();
    }

    public ImmutableUnlockingScript(final byte[] bytes) {
        super(bytes);
    }

    public ImmutableUnlockingScript(final Script script) {
        super(script);
    }

    @Override
    public ImmutableUnlockingScript asConst() {
        return this;
    }
}
