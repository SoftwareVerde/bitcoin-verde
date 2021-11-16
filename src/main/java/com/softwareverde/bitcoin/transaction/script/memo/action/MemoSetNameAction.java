package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoSetNameAction extends MemoStringAction {
    public MemoSetNameAction(final String value) {
        super(MemoScriptType.SET_NAME, value);
    }
}
