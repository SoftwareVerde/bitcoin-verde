package com.softwareverde.bitcoin.transaction.script.memo;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.Util;

public enum MemoScriptType {
    SET_NAME("6D01"),
    POST_MEMO("6D02"),
    REPLY_MEMO("6D03"),
    LIKE_MEMO("6D04"),
    SET_PROFILE_TEXT("6D05"),
    FOLLOW_USER("6D06"),
    UNFOLLOW_USER("6D07"),
    SET_PROFILE_PICTURE("6D0A"),
    // REPOST_MEMO("6D0B"),
    POST_TOPIC("6D0C"),
    FOLLOW_TOPIC("6D0D"),
    UNFOLLOW_TOPIC("6D0E"),
    CREATE_POLL("6D10"),
    ADD_POLL_OPTION("6D13"),
    VOTE_POLL("6D14"),
    MUTE_USER("6D16"),
    UNMUTE_USER("6D17"),
    SEND_MONEY("6D24"),
    SELL_TOKENS("6D30"),
    TOKEN_BUY_OFFER("6D31"),
    ATTACH_TOKEN_SALE_SIGNATURE("6D32"),
    // PIN_TOKEN_POST("6D35"),
    ;

    public static MemoScriptType fromBytes(final ByteArray byteArray) {
        for (final MemoScriptType memoScriptType : MemoScriptType.values()) {
            if (Util.areEqual(memoScriptType.getBytes(), byteArray)) {
                return memoScriptType;
            }
        }

        return null;
    }

    protected final ByteArray _value;
    MemoScriptType(final String value) {
        _value = ByteArray.fromHexString(value);
    }

    public ByteArray getBytes() {
        return _value;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
