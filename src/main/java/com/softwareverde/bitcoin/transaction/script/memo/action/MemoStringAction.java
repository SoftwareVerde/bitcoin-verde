package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;

public abstract class MemoStringAction extends MemoAction {
    protected static final Integer STRING_DEFAULT_MAX_BYTE_COUNT = 217;

    protected Integer _maxByteCount;
    protected String _value;

    @Override
    protected void _extendJson(final Json json) {
        json.put("value", _value);
    }

    protected MemoStringAction(final MemoScriptType memoScriptType, final String value) {
        this(memoScriptType, value, STRING_DEFAULT_MAX_BYTE_COUNT);
    }

    protected MemoStringAction(final MemoScriptType memoScriptType, final String value, final Integer maxByteCount) {
        super(memoScriptType);
        _value = value;
        _maxByteCount = maxByteCount;
    }

    public String getValue() {
        return _value;
    }

    public Integer getMaxByteCount() {
        return _maxByteCount;
    }
}
