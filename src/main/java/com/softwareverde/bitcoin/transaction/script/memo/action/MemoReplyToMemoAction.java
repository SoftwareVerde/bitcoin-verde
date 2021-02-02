package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;

public class MemoReplyToMemoAction extends MemoTransactionHashAction {
    protected static final Integer MESSAGE_MAX_BYTE_COUNT = (MemoStringAction.STRING_DEFAULT_MAX_BYTE_COUNT - Sha256Hash.BYTE_COUNT - 1);
    protected String _message;

    @Override
    protected void _extendJson(final Json json) {
        super._extendJson(json);
        json.put(JsonFields.STRING_VALUE, _message);
    }

    public MemoReplyToMemoAction(final Sha256Hash transactionHash, final String message) {
        super(MemoScriptType.REPLY_MEMO, transactionHash);

        _message = message;
    }
}
