package com.softwareverde.bitcoin.rpc;

public interface RpcNotificationCallback {
    void onNewNotification(RpcNotification rpcNotification);
}