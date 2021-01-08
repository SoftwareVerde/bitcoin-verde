package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;

public interface BitcoinMiningRpcConnector extends BitcoinRpcConnector {
    BlockTemplate getBlockTemplate(Monitor monitor);
    Boolean validateBlockTemplate(BlockTemplate blockTemplate, Monitor monitor);
    Boolean submitBlock(Block block, Monitor monitor);

    default BlockTemplate getBlockTemplate() {
        return this.getBlockTemplate(null);
    }
    default Boolean validateBlockTemplate(BlockTemplate blockTemplate) {
        return this.validateBlockTemplate(blockTemplate, null);
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
