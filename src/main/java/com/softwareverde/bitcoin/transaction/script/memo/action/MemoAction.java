package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public abstract class MemoAction implements Jsonable {
    protected final MemoScriptType _memoScriptType;

    protected abstract void _extendJson(final Json json);

    public MemoAction(final MemoScriptType memoScriptType) {
        _memoScriptType = memoScriptType;
    }

    public MemoScriptType getMemoScriptType() {
        return _memoScriptType;
    }

    @Override
    public Json toJson() {
        final Json json = new Json(false);

        json.put("type", _memoScriptType);

        return json;
    }
}
