package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressType;
import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.json.Json;

public abstract class MemoAddressAction extends MemoAction {
    protected final Address _address;

    @Override
    protected void _extendJson(final Json json) {
        final ParsedAddress parsedAddress = new ParsedAddress(AddressType.P2PKH, false, _address);
        json.put(JsonFields.ADDRESS_BASE_58, parsedAddress.toBase58CheckEncoded());
        json.put(JsonFields.ADDRESS_BASE_32, parsedAddress.toBase32CheckEncoded());
    }

    protected MemoAddressAction(final MemoScriptType memoScriptType, final Address address) {
        super(memoScriptType);
        _address = address;
    }

    public Address getAddress() {
        return _address;
    }
}
