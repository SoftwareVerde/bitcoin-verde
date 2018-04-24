package com.softwareverde.bitcoin.transaction.script.unlocking;

import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;

public class MutableUnlockingScript extends MutableScript implements UnlockingScript {

    protected MutableUnlockingScript() {
        super();
    }

    public MutableUnlockingScript(final Script script) {
        super(script);
    }

    @Override
    public ImmutableUnlockingScript asConst() {
        return new ImmutableUnlockingScript(this);
    }
}
