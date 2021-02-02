package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;

public class MemoPostTopicAction extends MemoStringAction {
    protected static final Integer MAX_TOTAL_BYTE_COUNT = MemoStringAction.STRING_DEFAULT_MAX_BYTE_COUNT;
    protected static final Integer MAX_TOPIC_NAME_BYTE_COUNT = 214;

    protected final String _topic;

    @Override
    protected void _extendJson(final Json json) {
        super._extendJson(json);
        json.put(JsonFields.TOPIC_NAME, _topic);
    }

    public MemoPostTopicAction(final String topic, final String topicMessage) {
        super(MemoScriptType.POST_TOPIC, topicMessage);
        _topic = topic;
    }

    public String getTopic() {
        return _topic;
    }

}
