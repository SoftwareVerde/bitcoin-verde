package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;

public abstract class MemoStringAction extends MemoAction {
    protected static final Integer STRING_DEFAULT_MAX_BYTE_COUNT = 217;

    protected Integer _maxByteCount;
    protected String _content;

    @Override
    protected void _extendJson(final Json json) {
        json.put(JsonFields.STRING_VALUE, _content);
    }

    protected MemoStringAction(final MemoScriptType memoScriptType, final String content) {
        this(memoScriptType, content, STRING_DEFAULT_MAX_BYTE_COUNT);
    }

    protected MemoStringAction(final MemoScriptType memoScriptType, final String content, final Integer maxByteCount) {
        super(memoScriptType);
        _content = content;
        _maxByteCount = maxByteCount;
    }

    public String getContent() {
        return _content;
    }

    public Integer getMaxByteCount() {
        return _maxByteCount;
    }
}
