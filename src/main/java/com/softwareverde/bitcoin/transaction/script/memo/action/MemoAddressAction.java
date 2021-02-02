package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;

public abstract class MemoAddressAction extends MemoAction {
    protected Address _address;

    @Override
    protected void _extendJson(final Json json) {
        json.put(JsonFields.ADDRESS_BASE_58, _address.toBase58CheckEncoded());
        json.put(JsonFields.ADDRESS_BASE_32, _address.toBase32CheckEncoded());
    }

    protected MemoAddressAction(final MemoScriptType memoScriptType, final Address address) {
        super(memoScriptType);
        _address = address;
    }

    public Address getAddress() {
        return _address;
    }
}
