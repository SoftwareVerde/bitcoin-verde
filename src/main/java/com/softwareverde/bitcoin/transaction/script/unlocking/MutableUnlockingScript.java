package com.softwareverde.bitcoin.transaction.script.unlocking;

import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.constable.bytearray.ByteArray;

public class MutableUnlockingScript extends MutableScript implements UnlockingScript {

    public MutableUnlockingScript() {
        super();
    }

    public MutableUnlockingScript(final ByteArray bytes) {
        super(bytes);
    }

    public MutableUnlockingScript(final Script script) {
        super(script);
    }

    @Override
    public ImmutableUnlockingScript asConst() {
        return new ImmutableUnlockingScript(this);
    }
}
