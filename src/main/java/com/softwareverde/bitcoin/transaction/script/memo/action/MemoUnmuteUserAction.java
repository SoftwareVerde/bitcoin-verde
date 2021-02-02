package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoUnmuteUserAction extends MemoAddressAction {
    public MemoUnmuteUserAction(final Address address) {
        super(MemoScriptType.UNMUTE_USER, address);
    }
}
