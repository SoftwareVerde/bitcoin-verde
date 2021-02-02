package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;

public class MemoAddPollOptionAction extends MemoTransactionHashAction {
    protected static final Integer OPTION_TEXT_MAX_BYTE_COUNT = 184;
    protected String _optionText;

    @Override
    protected void _extendJson(final Json json) {
        super._extendJson(json);
        json.put(JsonFields.STRING_VALUE, _optionText);
    }

    public MemoAddPollOptionAction(final Sha256Hash transactionHash, final String optionText) {
        super(MemoScriptType.ADD_POLL_OPTION, transactionHash);

        _optionText = optionText;
    }

    public String getOptionText() {
        return _optionText;
    }
}
