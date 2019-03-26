package com.softwareverde.bitcoin.server.message.type;

import com.softwareverde.bitcoin.util.ByteUtil;

public enum MessageType {
    SYNCHRONIZE_VERSION("version"), ACKNOWLEDGE_VERSION("verack"),
    PING("ping"), PONG("pong"),
    NODE_ADDRESSES("addr"),

    QUERY_BLOCKS("getblocks"), INVENTORY("inv"),
    REQUEST_BLOCK_HEADERS("getheaders"), BLOCK_HEADERS("headers"),
    REQUEST_DATA("getdata"), BLOCK("block"), TRANSACTION("tx"),

    NOT_FOUND("notfound"), ERROR("reject"),

    ENABLE_NEW_BLOCKS_VIA_HEADERS("sendheaders"),
    ENABLE_COMPACT_BLOCKS("sendcmpct"),

    REQUEST_EXTRA_THIN_BLOCK("get_xthin"), EXTRA_THIN_BLOCK("xthinblock"), THIN_BLOCK("thinblock"),
    REQUEST_EXTRA_THIN_TRANSACTIONS("get_xblocktx"), THIN_TRANSACTIONS("xblocktx"),

    FEE_FILTER("feefilter"), REQUEST_PEERS("getaddr"),

    SET_TRANSACTION_BLOOM_FILTER("filterload"), UPDATE_TRANSACTION_BLOOM_FILTER("filteradd"), CLEAR_TRANSACTION_BLOOM_FILTER("filterclear");

    public static MessageType fromBytes(final byte[] bytes) {
        for (final MessageType command : MessageType.values()) {
            if (ByteUtil.areEqual(command._bytes, bytes)) {
                return command;
            }
        }
        return null;
    }

    private final byte[] _bytes = new byte[12];
    private final String _value;

    MessageType(final String value) {
        _value = value;
        final byte[] valueBytes = value.getBytes();

        for (int i=0; i<_bytes.length; ++i) {
            _bytes[i] = (i<valueBytes.length ? valueBytes[i] : 0x00);
        }
    }

    public byte[] getBytes() {
        return ByteUtil.copyBytes(_bytes);
    }

    public String getValue() {
        return _value;
    }
}
