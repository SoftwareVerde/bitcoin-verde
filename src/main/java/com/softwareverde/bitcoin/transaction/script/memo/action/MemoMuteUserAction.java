package com.softwareverde.bitcoin.transaction.script.memo.action;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;

public class MemoMuteUserAction extends MemoAddressAction {
    public MemoMuteUserAction(final Address address) {
        super(MemoScriptType.MUTE_USER, address);
    }
}
