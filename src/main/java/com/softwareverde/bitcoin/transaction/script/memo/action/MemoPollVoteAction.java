package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;

public class MemoPollVoteAction extends MemoTransactionHashAction {
    protected static final Integer COMMENT_MAX_BYTE_COUNT = 184;

    protected String _comment;

    @Override
    protected void _extendJson(final Json json) {
        super._extendJson(json);
        json.put(JsonFields.STRING_VALUE, _comment);
    }

    public MemoPollVoteAction(final Sha256Hash transactionHash, final String comment) {
        super(MemoScriptType.VOTE_POLL, transactionHash);

        _comment = comment;
    }

    public String getComment() {
        return _comment;
    }
}
