package com.softwareverde.bitcoin.rpc;

import com.softwareverde.constable.bytearray.ByteArray;

public class RpcNotification {
    public final RpcNotificationType rpcNotificationType;
    public final ByteArray payload;

    public RpcNotification(final RpcNotificationType rpcNotificationType, final ByteArray payload) {
        this.rpcNotificationType = rpcNotificationType;
        this.payload = payload;
    }
}
