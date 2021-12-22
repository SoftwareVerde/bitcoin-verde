package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;

public interface BitcoinMiningRpcConnector extends BitcoinRpcConnector {
    BlockTemplate getBlockTemplate(Monitor monitor);
    Boolean validateBlockTemplate(Block block, Monitor monitor);
    Boolean submitBlock(Block block, Monitor monitor);

    default BlockTemplate getBlockTemplate() {
        return this.getBlockTemplate(null);
    }
    default Boolean validateBlockTemplate(Block block) {
        return this.validateBlockTemplate(block, null);
    }
    default Boolean submitBlock(Block block) {
        return this.submitBlock(block, null);
    }

    Boolean supportsNotifications();
    Boolean supportsNotification(RpcNotificationType rpcNotificationType);
    void subscribeToNotifications(RpcNotificationCallback rpcNotificationCallback);
    void unsubscribeToNotifications();

    @Override
    String toString();
}
