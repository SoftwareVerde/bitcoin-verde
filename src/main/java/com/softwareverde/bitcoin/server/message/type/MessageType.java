package com.softwareverde.bitcoin.server.message.type;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;

public class MessageType {
    public static final Integer BYTE_COUNT = 12;

    public static final MessageType SYNCHRONIZE_VERSION = new MessageType("version");
    public static final MessageType ACKNOWLEDGE_VERSION = new MessageType("verack");

    public static final MessageType PING = new MessageType("ping");
    public static final MessageType PONG = new MessageType("pong");

    public static final MessageType NODE_ADDRESSES = new MessageType("addr");

    public static final MessageType QUERY_BLOCKS = new MessageType("getblocks");
    public static final MessageType INVENTORY = new MessageType("inv");
    public static final MessageType QUERY_UNCONFIRMED_TRANSACTIONS = new MessageType("mempool");

    public static final MessageType REQUEST_BLOCK_HEADERS = new MessageType("getheaders");
    public static final MessageType BLOCK_HEADERS = new MessageType("headers");
    public static final MessageType REQUEST_DATA = new MessageType("getdata");
    public static final MessageType BLOCK = new MessageType("block", true);
    public static final MessageType TRANSACTION = new MessageType("tx");
    public static final MessageType MERKLE_BLOCK = new MessageType("merkleblock", true);

    public static final MessageType NOT_FOUND = new MessageType("notfound");
    public static final MessageType ERROR = new MessageType("reject");

    public static final MessageType ENABLE_NEW_BLOCKS_VIA_HEADERS = new MessageType("sendheaders");
    public static final MessageType ENABLE_COMPACT_BLOCKS = new MessageType("sendcmpct");

    public static final MessageType REQUEST_EXTRA_THIN_BLOCK = new MessageType("get_xthin");
    public static final MessageType EXTRA_THIN_BLOCK = new MessageType("xthinblock", true);
    public static final MessageType THIN_BLOCK = new MessageType("thinblock", true);
    public static final MessageType REQUEST_EXTRA_THIN_TRANSACTIONS = new MessageType("get_xblocktx");
    public static final MessageType THIN_TRANSACTIONS = new MessageType("xblocktx", true);

    public static final MessageType FEE_FILTER = new MessageType("feefilter");
    public static final MessageType REQUEST_PEERS = new MessageType("getaddr");

    public static final MessageType SET_TRANSACTION_BLOOM_FILTER = new MessageType("filterload");
    public static final MessageType UPDATE_TRANSACTION_BLOOM_FILTER = new MessageType("filteradd");
    public static final MessageType CLEAR_TRANSACTION_BLOOM_FILTER = new MessageType("filterclear");

    public static final MessageType DOUBLE_SPEND_PROOF = new MessageType("dsproof-beta");

    // BitcoinVerde Messages
    public static final MessageType QUERY_ADDRESS_BLOCKS = new MessageType("addrblocks", true);
    public static final MessageType ENABLE_SLP_TRANSACTIONS = new MessageType("sendslp", true);
    public static final MessageType QUERY_SLP_STATUS = new MessageType("getslpstatus", true);

    protected final ByteArray _bytes;
    protected final String _value;
    protected final Boolean _isLargeMessage;

    protected MessageType(final String value) {
        this(value, false);
    }

    protected MessageType(final String value, final Boolean isLargeMessage) {
        _value = value;
        _isLargeMessage = isLargeMessage;

        final MutableByteArray mutableByteArray = new MutableByteArray(BYTE_COUNT);
        final byte[] valueBytes = value.getBytes();
        ByteUtil.setBytes(mutableByteArray.unwrap(), valueBytes);

        _bytes = mutableByteArray.asConst();
    }

    public ByteArray getBytes() {
        return _bytes;
    }

    public String getValue() {
        return _value;
    }

    public Boolean isLargeMessage() {
        return _isLargeMessage;
    }


    @Override
    public boolean equals(final Object object) {
        if (this == object) { return true; }
        if (! (object instanceof MessageType)) { return false; }

        final MessageType messageType = (MessageType) object;
        return ( Util.areEqual(_bytes, messageType._bytes) && Util.areEqual(_value, messageType._value) );
    }

    @Override
    public int hashCode() {
        return _value.hashCode();
    }

    @Override
    public String toString() {
        return _value;
    }
}
