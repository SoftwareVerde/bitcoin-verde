package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinarySocket;

public class NodeInitializer {
    public interface TransactionsAnnouncementCallbackFactory {
        BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(BitcoinNode bitcoinNode);
    }

    public static class Properties {
        public SynchronizationStatus synchronizationStatus;
        public BitcoinNode.BlockInventoryMessageCallback blockInventoryMessageHandler;
        public TransactionsAnnouncementCallbackFactory transactionsAnnouncementCallbackFactory;
        public BitcoinNode.QueryBlocksCallback queryBlocksCallback;
        public BitcoinNode.QueryBlockHeadersCallback queryBlockHeadersCallback;
        public BitcoinNode.RequestDataCallback requestDataCallback;
        public BitcoinNode.RequestSpvBlocksCallback requestSpvBlocksCallback;
        public BitcoinNode.RequestSlpTransactionsCallback requestSlpTransactionsCallback;
        public ThreadPoolFactory threadPoolFactory;
        public LocalNodeFeatures localNodeFeatures;
        public BitcoinNode.RequestPeersHandler requestPeersHandler;
        public BitcoinNode.QueryUnconfirmedTransactionsCallback queryUnconfirmedTransactionsCallback;
        public BitcoinNode.SpvBlockInventoryMessageCallback spvBlockInventoryMessageCallback;
        public BitcoinBinaryPacketFormat binaryPacketFormat;
        public BitcoinNode.OnNewBloomFilterCallback onNewBloomFilterCallback;
    }

    protected final SynchronizationStatus _synchronizationStatus;
    protected final BitcoinNode.BlockInventoryMessageCallback _blockInventoryMessageHandler;
    protected final TransactionsAnnouncementCallbackFactory _transactionsAnnouncementCallbackFactory;
    protected final BitcoinNode.QueryBlocksCallback _queryBlocksCallback;
    protected final BitcoinNode.QueryBlockHeadersCallback _queryBlockHeadersCallback;
    protected final BitcoinNode.RequestDataCallback _requestDataCallback;
    protected final BitcoinNode.RequestSpvBlocksCallback _requestSpvBlocksCallback;
    protected final BitcoinNode.RequestSlpTransactionsCallback _requestSlpTransactionsCallback;
    protected final ThreadPoolFactory _threadPoolFactory;
    protected final LocalNodeFeatures _localNodeFeatures;
    protected final BitcoinNode.RequestPeersHandler _requestPeersHandler;
    protected final BitcoinNode.QueryUnconfirmedTransactionsCallback _queryUnconfirmedTransactionsCallback;
    protected final BitcoinNode.SpvBlockInventoryMessageCallback _spvBlockInventoryMessageCallback;
    protected final BitcoinBinaryPacketFormat _binaryPacketFormat;
    protected final BitcoinNode.OnNewBloomFilterCallback _onNewBloomFilterCallback;

    protected void _initializeNode(final BitcoinNode bitcoinNode) {
        bitcoinNode.setSynchronizationStatusHandler(_synchronizationStatus);

        bitcoinNode.setQueryBlocksCallback(_queryBlocksCallback);
        bitcoinNode.setQueryBlockHeadersCallback(_queryBlockHeadersCallback);
        bitcoinNode.setRequestDataCallback(_requestDataCallback);
        bitcoinNode.setRequestSpvBlocksCallback(_requestSpvBlocksCallback);
        bitcoinNode.setRequestSlpTransactionsCallback(_requestSlpTransactionsCallback);
        bitcoinNode.setSpvBlockInventoryMessageCallback(_spvBlockInventoryMessageCallback);

        bitcoinNode.setBlockInventoryMessageHandler(_blockInventoryMessageHandler);
        bitcoinNode.setQueryUnconfirmedTransactionsCallback(_queryUnconfirmedTransactionsCallback);

        final TransactionsAnnouncementCallbackFactory transactionsAnnouncementCallbackFactory = _transactionsAnnouncementCallbackFactory;
        if (transactionsAnnouncementCallbackFactory != null) {
            final BitcoinNode.TransactionInventoryMessageCallback transactionsAnnouncementCallback = transactionsAnnouncementCallbackFactory.createTransactionsAnnouncementCallback(bitcoinNode);
            bitcoinNode.setTransactionsAnnouncementCallback(transactionsAnnouncementCallback);
        }

        bitcoinNode.setRequestPeersHandler(_requestPeersHandler);
        bitcoinNode.setOnNewBloomFilterCallback(_onNewBloomFilterCallback);
    }

    public NodeInitializer(final Properties properties) {
        _synchronizationStatus = properties.synchronizationStatus;
        _blockInventoryMessageHandler = properties.blockInventoryMessageHandler;
        _transactionsAnnouncementCallbackFactory = properties.transactionsAnnouncementCallbackFactory;
        _queryBlocksCallback = properties.queryBlocksCallback;
        _queryBlockHeadersCallback = properties.queryBlockHeadersCallback;
        _requestDataCallback = properties.requestDataCallback;
        _requestSpvBlocksCallback = properties.requestSpvBlocksCallback;
        _requestSlpTransactionsCallback = properties.requestSlpTransactionsCallback;
        _threadPoolFactory = properties.threadPoolFactory;
        _localNodeFeatures = properties.localNodeFeatures;
        _requestPeersHandler = properties.requestPeersHandler;
        _queryUnconfirmedTransactionsCallback = properties.queryUnconfirmedTransactionsCallback;
        _spvBlockInventoryMessageCallback = properties.spvBlockInventoryMessageCallback;
        _binaryPacketFormat = properties.binaryPacketFormat;
        _onNewBloomFilterCallback = properties.onNewBloomFilterCallback;
    }

    public BitcoinNode initializeNode(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        final Integer port = nodeIpAddress.getPort();
        final String host = ip.toString();

        final BitcoinNode bitcoinNode = new BitcoinNode(host, port, _binaryPacketFormat, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
        _initializeNode(bitcoinNode);
        return bitcoinNode;
    }

    public BitcoinNode initializeNode(final String host, final Integer port) {
        final BitcoinNode bitcoinNode = new BitcoinNode(host, port, _binaryPacketFormat, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
        _initializeNode(bitcoinNode);
        return bitcoinNode;
    }

    public BitcoinNode initializeNode(final BinarySocket binarySocket) {
        final BitcoinNode node = new BitcoinNode(binarySocket, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
        _initializeNode(node);
        return node;
    }

    public void initializeNode(final BitcoinNode bitcoinNode) {
        _initializeNode(bitcoinNode);
    }

    public BitcoinBinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }
}
