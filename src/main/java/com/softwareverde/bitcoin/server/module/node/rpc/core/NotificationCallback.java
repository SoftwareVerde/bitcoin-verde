package com.softwareverde.bitcoin.server.module.node.rpc.core;

public interface NotificationCallback {
    void onNewNotification(Notification notification);
}