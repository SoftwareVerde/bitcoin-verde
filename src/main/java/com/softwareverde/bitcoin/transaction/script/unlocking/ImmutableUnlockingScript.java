package com.softwareverde.bitcoin.transaction.script.unlocking;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;

public class ImmutableUnlockingScript extends ImmutableScript implements UnlockingScript {
    public ImmutableUnlockingScript(final byte[] bytes) {
        super(bytes);
    }

    @Override
    public ImmutableUnlockingScript asConst() {
        return this;
    }
}
