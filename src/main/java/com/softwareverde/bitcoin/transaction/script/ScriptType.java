package com.softwareverde.bitcoin.transaction.script;

public enum ScriptType {
    UNKNOWN(1L),
    CUSTOM_SCRIPT(2L),
    PAY_TO_PUBLIC_KEY(3L),
    PAY_TO_PUBLIC_KEY_HASH(4L),
    PAY_TO_SCRIPT_HASH(5L);

    protected final ScriptTypeId _scriptTypeId;

    ScriptType(final Long id) {
        _scriptTypeId = ScriptTypeId.wrap(id);
    }

    public ScriptTypeId getScriptTypeId() {
        return _scriptTypeId;
    }
}
