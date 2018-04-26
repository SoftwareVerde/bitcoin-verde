package com.softwareverde.bitcoin.transaction.script.locking;

import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;

public class MutableLockingScript extends MutableScript implements LockingScript {

    public MutableLockingScript() {
        super();
    }

    public MutableLockingScript(final byte[] bytes) {
        super(bytes);
    }

    public MutableLockingScript(final Script script) {
        super(script);
    }

    @Override
    public Boolean isPayToScriptHash() {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        return scriptPatternMatcher.matchesPayToScriptHashFormat(this);
    }

    @Override
    public ImmutableLockingScript asConst() {
        return new ImmutableLockingScript(this);
    }
}
