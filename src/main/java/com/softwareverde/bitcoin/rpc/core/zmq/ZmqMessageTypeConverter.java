package com.softwareverde.bitcoin.rpc.core.zmq;

import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.StringUtil;

public class ZmqMessageTypeConverter {
    public static RpcNotificationType fromPublishString(final String string) {
        if (string == null) { return null; }

        switch (string.toLowerCase()) {
            case "pubhashblock": { return RpcNotificationType.BLOCK_HASH; }
            case "pubhashtx": { return RpcNotificationType.TRANSACTION_HASH; }
            case "pubrawblock": { return RpcNotificationType.BLOCK; }
            case "pubrawtx": { return RpcNotificationType.TRANSACTION; }
            default: { return null; }
        }
    }

    public static String toPublishString(final RpcNotificationType rpcNotificationType) {
        if (rpcNotificationType == null) { return null; }

        switch (rpcNotificationType) {
            case BLOCK_HASH: { return "pubhashblock"; }
            case TRANSACTION_HASH: { return "pubhashtx"; }
            case BLOCK: { return "pubrawblock"; }
            case TRANSACTION: { return "pubrawtx"; }
            default: { return null; }
        }
    }

    public static RpcNotificationType fromSubscriptionString(final String string) {
        if (string == null) { return null; }

        switch (string.toLowerCase()) {
            case "hashblock": { return RpcNotificationType.BLOCK_HASH; }
            case "hashtx": { return RpcNotificationType.TRANSACTION_HASH; }
            case "rawblock": { return RpcNotificationType.BLOCK; }
            case "rawtx": { return RpcNotificationType.TRANSACTION; }
            default: { return null; }
        }
    }

    public static String toSubscriptionString(final RpcNotificationType rpcNotificationType) {
        if (rpcNotificationType == null) { return null; }

        switch (rpcNotificationType) {
            case BLOCK_HASH: { return "hashblock"; }
            case TRANSACTION_HASH: { return "hashtx"; }
            case BLOCK: { return "rawblock"; }
            case TRANSACTION: { return "rawtx"; }
            default: { return null; }
        }
    }

    public static RpcNotificationType fromMessageBytes(final ByteArray bytes) {
        final String bytesAsString = StringUtil.bytesToString(bytes.getBytes());
        return ZmqMessageTypeConverter.fromSubscriptionString(bytesAsString);
    }

    protected ZmqMessageTypeConverter() { }
}
