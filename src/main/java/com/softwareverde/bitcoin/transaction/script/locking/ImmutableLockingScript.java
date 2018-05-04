package com.softwareverde.bitcoin.transaction.script.locking;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;

public class ImmutableLockingScript extends ImmutableScript implements LockingScript {

    protected ImmutableLockingScript() {
        super();
    }

    public ImmutableLockingScript(final byte[] bytes) {
        super(bytes);
    }

    public ImmutableLockingScript(final Script script) {
        super(script);
    }

    @Override
    public Boolean isPayToScriptHash() {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        return scriptPatternMatcher.matchesPayToScriptHashFormat(this);
    }

    @Override
    public ImmutableLockingScript asConst() {
        return this;
    }
}
