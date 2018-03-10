package com.softwareverde.bitcoin.transaction.script.locking;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;

public class ImmutableLockingScript extends ImmutableScript implements LockingScript {
    public ImmutableLockingScript(final byte[] bytes) {
        super(bytes);
    }

    @Override
    public ImmutableLockingScript asConst() {
        return this;
    }
}
