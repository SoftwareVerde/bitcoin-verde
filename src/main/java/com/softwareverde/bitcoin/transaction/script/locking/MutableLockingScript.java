package com.softwareverde.bitcoin.transaction.script.locking;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;

public class MutableLockingScript extends ImmutableScript implements LockingScript {

    protected MutableLockingScript() {
        super();
    }

    public MutableLockingScript(final byte[] bytes) {
        super(bytes);
    }

    public MutableLockingScript(final Script script) {
        super(script);
    }

    @Override
    public ImmutableLockingScript asConst() {
        return new ImmutableLockingScript(this);
    }
}
