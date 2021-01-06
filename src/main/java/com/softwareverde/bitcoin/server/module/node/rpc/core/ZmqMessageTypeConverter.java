package com.softwareverde.bitcoin.server.module.node.rpc.core;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.StringUtil;

public class ZmqMessageTypeConverter {
    public static NotificationType fromPublishString(final String string) {
        if (string == null) { return null; }

        switch (string.toLowerCase()) {
            case "pubhashblock": { return NotificationType.BLOCK_HASH; }
            case "pubhashtx": { return NotificationType.TRANSACTION_HASH; }
            case "pubrawblock": { return NotificationType.BLOCK; }
            case "pubrawtx": { return NotificationType.TRANSACTION; }
            default: { return null; }
        }
    }

    public static String toPublishString(final NotificationType notificationType) {
        if (notificationType == null) { return null; }

        switch (notificationType) {
            case BLOCK_HASH: { return "pubhashblock"; }
            case TRANSACTION_HASH: { return "pubhashtx"; }
            case BLOCK: { return "pubrawblock"; }
            case TRANSACTION: { return "pubrawtx"; }
            default: { return null; }
        }
    }

    public static NotificationType fromSubscriptionString(final String string) {
        if (string == null) { return null; }

        switch (string.toLowerCase()) {
            case "hashblock": { return NotificationType.BLOCK_HASH; }
            case "hashtx": { return NotificationType.TRANSACTION_HASH; }
            case "rawblock": { return NotificationType.BLOCK; }
            case "rawtx": { return NotificationType.TRANSACTION; }
            default: { return null; }
        }
    }

    public static String toSubscriptionString(final NotificationType notificationType) {
        if (notificationType == null) { return null; }

        switch (notificationType) {
            case BLOCK_HASH: { return "hashblock"; }
            case TRANSACTION_HASH: { return "hashtx"; }
            case BLOCK: { return "rawblock"; }
            case TRANSACTION: { return "rawtx"; }
            default: { return null; }
        }
    }

    public static NotificationType fromMessageBytes(final ByteArray bytes) {
        final String bytesAsString = StringUtil.bytesToString(bytes.getBytes());
        return ZmqMessageTypeConverter.fromSubscriptionString(bytesAsString);
    }

    protected ZmqMessageTypeConverter() { }
}
