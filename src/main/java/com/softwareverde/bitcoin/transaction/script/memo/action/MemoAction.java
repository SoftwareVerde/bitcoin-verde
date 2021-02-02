package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public abstract class MemoAction implements Jsonable {
    protected static class JsonFields {
        public static final String SCRIPT_TYPE = "type";
        public static final String STRING_VALUE = "content";
        public static final String TOPIC_NAME = "topic";
        public static final String TRANSACTION_HASH = "transactionHash";
        public static final String POLL_TYPE = "pollType";
        public static final String POLL_OPTION_COUNT = "pollOptionCount";
        public static final String ADDRESS_BASE_58 = "address";
        public static final String ADDRESS_BASE_32 = "cashAddress";
    }

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

        json.put(JsonFields.SCRIPT_TYPE, _memoScriptType);
        _extendJson(json);

        return json;
    }
}
