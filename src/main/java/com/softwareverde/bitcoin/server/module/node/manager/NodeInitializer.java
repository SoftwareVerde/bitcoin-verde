package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
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
        public ThreadPoolFactory threadPoolFactory;
        public LocalNodeFeatures localNodeFeatures;
        public BitcoinNode.RequestPeersHandler requestPeersHandler;
        public BitcoinNode.QueryUnconfirmedTransactionsCallback queryUnconfirmedTransactionsCallback;
    }

    protected final SynchronizationStatus _synchronizationStatus;
    protected final BitcoinNode.BlockInventoryMessageCallback _blockInventoryMessageHandler;
    protected final TransactionsAnnouncementCallbackFactory _transactionsAnnouncementCallbackFactory;
    protected final BitcoinNode.QueryBlocksCallback _queryBlocksCallback;
    protected final BitcoinNode.QueryBlockHeadersCallback _queryBlockHeadersCallback;
    protected final BitcoinNode.RequestDataCallback _requestDataCallback;
    protected final ThreadPoolFactory _threadPoolFactory;
    protected final LocalNodeFeatures _localNodeFeatures;
    protected final BitcoinNode.RequestPeersHandler _requestPeersHandler;
    protected final BitcoinNode.QueryUnconfirmedTransactionsCallback _queryUnconfirmedTransactionsCallback;

    protected void _initializeNode(final BitcoinNode bitcoinNode) {
        bitcoinNode.setSynchronizationStatusHandler(_synchronizationStatus);

        bitcoinNode.setQueryBlocksCallback(_queryBlocksCallback);
        bitcoinNode.setQueryBlockHeadersCallback(_queryBlockHeadersCallback);
        bitcoinNode.setRequestDataCallback(_requestDataCallback);

        bitcoinNode.setBlockInventoryMessageHandler(_blockInventoryMessageHandler);
        bitcoinNode.setQueryUnconfirmedTransactionsCallback(_queryUnconfirmedTransactionsCallback);

        final TransactionsAnnouncementCallbackFactory transactionsAnnouncementCallbackFactory = _transactionsAnnouncementCallbackFactory;
        if (transactionsAnnouncementCallbackFactory != null) {
            final BitcoinNode.TransactionInventoryMessageCallback transactionsAnnouncementCallback = transactionsAnnouncementCallbackFactory.createTransactionsAnnouncementCallback(bitcoinNode);
            bitcoinNode.setTransactionsAnnouncementCallback(transactionsAnnouncementCallback);
        }

        bitcoinNode.setRequestPeersHandler(_requestPeersHandler);
    }

    public NodeInitializer(final Properties properties) {
        this(properties.synchronizationStatus, properties.blockInventoryMessageHandler, properties.transactionsAnnouncementCallbackFactory,
            properties.queryBlocksCallback, properties.queryBlockHeadersCallback, properties.requestDataCallback,
            properties.threadPoolFactory, properties.localNodeFeatures, properties.requestPeersHandler, properties.queryUnconfirmedTransactionsCallback
        );
    }

    public NodeInitializer(final SynchronizationStatus synchronizationStatus, final BitcoinNode.BlockInventoryMessageCallback blockInventoryMessageHandler, final TransactionsAnnouncementCallbackFactory transactionsAnnouncementCallbackFactory, final BitcoinNode.QueryBlocksCallback queryBlocksCallback, final BitcoinNode.QueryBlockHeadersCallback queryBlockHeadersCallback, final BitcoinNode.RequestDataCallback requestDataCallback, final ThreadPoolFactory threadPoolFactory, final LocalNodeFeatures localNodeFeatures, final BitcoinNode.RequestPeersHandler requestPeersHandler, final BitcoinNode.QueryUnconfirmedTransactionsCallback queryUnconfirmedTransactionsCallback) {
        _synchronizationStatus = synchronizationStatus;
        _blockInventoryMessageHandler = blockInventoryMessageHandler;
        _transactionsAnnouncementCallbackFactory = transactionsAnnouncementCallbackFactory;
        _queryBlocksCallback = queryBlocksCallback;
        _queryBlockHeadersCallback = queryBlockHeadersCallback;
        _requestDataCallback = requestDataCallback;
        _threadPoolFactory = threadPoolFactory;
        _localNodeFeatures = localNodeFeatures;
        _requestPeersHandler = requestPeersHandler;
        _queryUnconfirmedTransactionsCallback = queryUnconfirmedTransactionsCallback;
    }

    public BitcoinNode initializeNode(final String host, final Integer port) {
        final BitcoinNode node = new BitcoinNode(host, port, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
        _initializeNode(node);
        return node;
    }

    public BitcoinNode initializeNode(final BinarySocket binarySocket) {
        final BitcoinNode node = new BitcoinNode(binarySocket, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
        _initializeNode(node);
        return node;
    }

    public void initializeNode(final BitcoinNode bitcoinNode) {
        _initializeNode(bitcoinNode);
    }
}
