package com.softwareverde.bitcoin.transaction.script;

public enum ScriptType {
    UNKNOWN(1L),
    CUSTOM_SCRIPT(2L),
    PAY_TO_PUBLIC_KEY(3L),
    PAY_TO_PUBLIC_KEY_HASH(4L),
    PAY_TO_SCRIPT_HASH(5L),
    SLP_GENESIS_SCRIPT(6L),
    SLP_SEND_SCRIPT(7L),
    SLP_MINT_SCRIPT(8L),
    SLP_COMMIT_SCRIPT(9L),
    MEMO_SCRIPT(10L);

    public static Boolean isSlpScriptType(final ScriptType scriptType) {
        if (scriptType == null) { return false; }

        switch (scriptType) {
            case SLP_GENESIS_SCRIPT:
            case SLP_SEND_SCRIPT:
            case SLP_MINT_SCRIPT:
            case SLP_COMMIT_SCRIPT: {
                return true;
            }
            default: { return false; }
        }
    }

    public static Boolean isAddressScriptType(final ScriptType scriptType) {
        if (scriptType == null) { return false; }

        switch (scriptType) {
            case PAY_TO_PUBLIC_KEY:
            case PAY_TO_PUBLIC_KEY_HASH:
            case PAY_TO_SCRIPT_HASH: {
                return true;
            }
            default: { return false; }
        }
    }

    protected final ScriptTypeId _scriptTypeId;

    ScriptType(final Long id) {
        _scriptTypeId = ScriptTypeId.wrap(id);
    }

    public ScriptTypeId getScriptTypeId() {
        return _scriptTypeId;
    }
}
