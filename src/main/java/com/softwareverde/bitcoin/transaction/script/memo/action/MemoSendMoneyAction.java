package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;

public class MemoSendMoneyAction extends MemoAddressAction {
    protected static final Integer MESSAGE_MAX_BYTE_COUNT = 194;

    protected String _message;

    @Override
    protected void _extendJson(final Json json) {
        json.put("value", _message);
    }

    public MemoSendMoneyAction(final Address address, final String message) {
        super(MemoScriptType.SEND_MONEY, address);

        _message = message;
    }

    public String getMessage() {
        return _message;
    }

    public Integer getMaxByteCount() {
        return MESSAGE_MAX_BYTE_COUNT;
    }
}
