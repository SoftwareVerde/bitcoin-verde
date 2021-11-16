package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoFollowTopicAction extends MemoStringAction {
    public MemoFollowTopicAction(final String value) {
        super(MemoScriptType.FOLLOW_TOPIC, value, MemoPostTopicAction.MAX_TOPIC_NAME_BYTE_COUNT);
    }
}
