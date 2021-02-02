package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class MemoLikeMemoAction extends MemoTransactionHashAction {
    public MemoLikeMemoAction(final Sha256Hash transactionHash) {
        super(MemoScriptType.LIKE_MEMO, transactionHash);
    }
}
