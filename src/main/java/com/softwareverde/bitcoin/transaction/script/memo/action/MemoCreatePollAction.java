package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;

public class MemoCreatePollAction extends MemoStringAction {
    protected static final Integer QUESTION_MAX_BYTE_COUNT = 209;

    protected Integer _pollType;
    protected Integer _optionCount;

    @Override
    protected void _extendJson(final Json json) {
        json.put("pollType", _pollType);
        json.put("optionCount", _optionCount);
    }

    public MemoCreatePollAction(final Integer pollType, final Integer optionCount, final String value) {
        super(MemoScriptType.CREATE_POLL, value, QUESTION_MAX_BYTE_COUNT);

        _pollType = pollType;
        _optionCount = optionCount;
    }
}
