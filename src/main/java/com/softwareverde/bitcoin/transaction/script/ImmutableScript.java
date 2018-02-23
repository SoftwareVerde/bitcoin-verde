package com.softwareverde.bitcoin.transaction.script;

public class ImmutableScript extends MutableScript {
    public ImmutableScript(final byte[] bytes) {
        super(bytes);
    }

    public ImmutableScript(final Script script) {
        super(script);
    }
}
