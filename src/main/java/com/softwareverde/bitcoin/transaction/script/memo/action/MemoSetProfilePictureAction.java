package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoSetProfilePictureAction extends MemoStringAction {
    public MemoSetProfilePictureAction(final String value) {
        super(MemoScriptType.SET_PROFILE_PICTURE, value);
    }
}
