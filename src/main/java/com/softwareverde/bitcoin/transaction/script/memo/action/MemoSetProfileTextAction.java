package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoSetProfileTextAction extends MemoStringAction {
    public MemoSetProfileTextAction(final String value) {
        super(MemoScriptType.SET_PROFILE_TEXT, value);
    }
}
