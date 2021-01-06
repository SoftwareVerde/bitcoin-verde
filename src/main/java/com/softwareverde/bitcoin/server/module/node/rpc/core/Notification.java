package com.softwareverde.bitcoin.server.module.node.rpc.core;

import com.softwareverde.constable.bytearray.ByteArray;

public class Notification {
    public final NotificationType notificationType;
    public final ByteArray payload;

    public Notification(final NotificationType notificationType, final ByteArray payload) {
        this.notificationType = notificationType;
        this.payload = payload;
    }
}
