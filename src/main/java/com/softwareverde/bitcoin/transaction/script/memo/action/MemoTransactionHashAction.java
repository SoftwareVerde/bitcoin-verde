package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;

public abstract class MemoTransactionHashAction extends MemoAction {
    protected Sha256Hash _transactionHash;

    @Override
    protected void _extendJson(final Json json) {
        json.put(JsonFields.TRANSACTION_HASH, _transactionHash);
    }

    protected MemoTransactionHashAction(final MemoScriptType memoScriptType, final Sha256Hash transactionHash) {
        super(memoScriptType);
        _transactionHash = transactionHash;
    }

    public Sha256Hash getTransactionHash() {
        return _transactionHash;
    }
}
