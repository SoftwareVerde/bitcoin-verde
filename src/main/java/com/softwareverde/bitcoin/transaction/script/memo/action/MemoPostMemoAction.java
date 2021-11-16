package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoPostMemoAction extends MemoStringAction {
    public MemoPostMemoAction(final String value) {
        super(MemoScriptType.POST_MEMO, value);
    }
}
