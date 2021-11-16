package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoUnfollowTopicAction extends MemoStringAction {
    public MemoUnfollowTopicAction(final String value) {
        super(MemoScriptType.UNFOLLOW_TOPIC, value, MemoPostTopicAction.MAX_TOPIC_NAME_BYTE_COUNT);
    }
}
