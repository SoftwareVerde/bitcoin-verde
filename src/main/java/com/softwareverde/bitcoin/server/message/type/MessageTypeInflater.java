package com.softwareverde.bitcoin.server.message.type;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;

public class MessageTypeInflater {
    protected static final List<MessageType> MESSAGE_TYPES;
    static {
        final ImmutableListBuilder<MessageType> messageTypes = new ImmutableListBuilder<MessageType>();
        messageTypes.add(MessageType.SYNCHRONIZE_VERSION);
        messageTypes.add(MessageType.ACKNOWLEDGE_VERSION);
        messageTypes.add(MessageType.PING);
        messageTypes.add(MessageType.PONG);
        messageTypes.add(MessageType.NODE_ADDRESSES);
        messageTypes.add(MessageType.QUERY_BLOCKS);
        messageTypes.add(MessageType.INVENTORY);
        messageTypes.add(MessageType.QUERY_UNCONFIRMED_TRANSACTIONS);
        messageTypes.add(MessageType.REQUEST_BLOCK_HEADERS);
        messageTypes.add(MessageType.BLOCK_HEADERS);
        messageTypes.add(MessageType.REQUEST_DATA);
        messageTypes.add(MessageType.BLOCK);
        messageTypes.add(MessageType.TRANSACTION);
        messageTypes.add(MessageType.MERKLE_BLOCK);
        messageTypes.add(MessageType.NOT_FOUND);
        messageTypes.add(MessageType.ERROR);
        messageTypes.add(MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS);
        messageTypes.add(MessageType.ENABLE_COMPACT_BLOCKS);
        messageTypes.add(MessageType.REQUEST_EXTRA_THIN_BLOCK);
        messageTypes.add(MessageType.EXTRA_THIN_BLOCK);
        messageTypes.add(MessageType.THIN_BLOCK);
        messageTypes.add(MessageType.REQUEST_EXTRA_THIN_TRANSACTIONS);
        messageTypes.add(MessageType.THIN_TRANSACTIONS);
        messageTypes.add(MessageType.FEE_FILTER);
        messageTypes.add(MessageType.REQUEST_PEERS);
        messageTypes.add(MessageType.SET_TRANSACTION_BLOOM_FILTER);
        messageTypes.add(MessageType.UPDATE_TRANSACTION_BLOOM_FILTER);
        messageTypes.add(MessageType.CLEAR_TRANSACTION_BLOOM_FILTER);
        messageTypes.add(MessageType.DOUBLE_SPEND_PROOF);
        // Bitcoin Verde Messages
        messageTypes.add(MessageType.QUERY_ADDRESS_BLOCKS);
        messageTypes.add(MessageType.ENABLE_SLP_TRANSACTIONS);
        messageTypes.add(MessageType.QUERY_SLP_STATUS);

        MESSAGE_TYPES = messageTypes.build();
    }

    public MessageType fromBytes(final ByteArray byteArray) {
        for (final MessageType messageType : MESSAGE_TYPES) {
            if (ByteUtil.areEqual(messageType.getBytes(), byteArray)) {
                return messageType;
            }
        }
        return null;
    }
}
