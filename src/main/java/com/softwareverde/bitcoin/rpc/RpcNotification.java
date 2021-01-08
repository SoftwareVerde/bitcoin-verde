package com.softwareverde.bitcoin.rpc;

import com.softwareverde.constable.bytearray.ByteArray;

public class RpcNotification {
    public final RpcNotificationType _rpcNotificationType;
    public final ByteArray payload;

    public RpcNotification(final RpcNotificationType rpcNotificationType, final ByteArray payload) {
        this._rpcNotificationType = rpcNotificationType;
        this.payload = payload;
    }
}
