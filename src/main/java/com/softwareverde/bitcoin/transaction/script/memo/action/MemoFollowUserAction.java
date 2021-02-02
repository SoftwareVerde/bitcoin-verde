package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoFollowUserAction extends MemoAddressAction {
    public MemoFollowUserAction(final Address address) {
        super(MemoScriptType.FOLLOW_USER, address);
    }
}
