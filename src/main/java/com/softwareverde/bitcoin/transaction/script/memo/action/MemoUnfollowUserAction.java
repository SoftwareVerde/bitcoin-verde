package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoUnfollowUserAction extends MemoAddressAction {
    public MemoUnfollowUserAction(final Address address) {
        super(MemoScriptType.UNFOLLOW_USER, address);
    }
}
