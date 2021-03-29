package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.threadpool.ThreadPoolFactory;

public class NodeInitializer {
    public interface TransactionsAnnouncementHandlerFactory {
        BitcoinNode.TransactionInventoryAnnouncementHandler createTransactionsAnnouncementHandler(BitcoinNode bitcoinNode);
    }

    public interface DoubleSpendProofAnnouncementHandlerFactory {
        BitcoinNode.DoubleSpendProofAnnouncementHandler createDoubleSpendProofAnnouncementHandler(BitcoinNode bitcoinNode);
    }

    public static class Context {
        public SynchronizationStatus synchronizationStatus;
        public BitcoinNode.BlockInventoryAnnouncementHandler blockInventoryMessageHandler;
        public TransactionsAnnouncementHandlerFactory transactionsAnnouncementHandlerFactory;
        public DoubleSpendProofAnnouncementHandlerFactory doubleSpendProofAnnouncementHandlerFactory;
        public BitcoinNode.RequestBlockHashesHandler requestBlockHashesHandler;
        public BitcoinNode.RequestBlockHeadersHandler requestBlockHeadersHandler;
        public BitcoinNode.RequestDataHandler requestDataHandler;
        public BitcoinNode.RequestSpvBlocksHandler requestSpvBlocksHandler;
        public BitcoinNode.RequestSlpTransactionsHandler requestSlpTransactionsHandler;
        public ThreadPoolFactory threadPoolFactory;
        public LocalNodeFeatures localNodeFeatures;
        public BitcoinNode.RequestPeersHandler requestPeersHandler;
        public BitcoinNode.RequestUnconfirmedTransactionsHandler requestUnconfirmedTransactionsHandler;
        public BitcoinNode.SpvBlockInventoryAnnouncementHandler spvBlockInventoryAnnouncementHandler;
        public BitcoinBinaryPacketFormat binaryPacketFormat;
        public BitcoinNode.NewBloomFilterHandler newBloomFilterHandler;
    }

    protected final SynchronizationStatus _synchronizationStatus;
    protected final BitcoinNode.BlockInventoryAnnouncementHandler _blockInventoryAnnouncementHandler;
    protected final TransactionsAnnouncementHandlerFactory _transactionsAnnouncementHandlerFactory;
    protected final DoubleSpendProofAnnouncementHandlerFactory _doubleSpendProofAnnouncementHandlerFactory;
    protected final BitcoinNode.RequestBlockHashesHandler _requestBlockHashesHandler;
    protected final BitcoinNode.RequestBlockHeadersHandler _requestBlockHeadersHandler;
    protected final BitcoinNode.RequestDataHandler _requestDataHandler;
    protected final BitcoinNode.RequestSpvBlocksHandler _requestSpvBlocksHandler;
    protected final BitcoinNode.RequestSlpTransactionsHandler _requestSlpTransactionsHandler;
    protected final ThreadPoolFactory _threadPoolFactory;
    protected final LocalNodeFeatures _localNodeFeatures;
    protected final BitcoinNode.RequestPeersHandler _requestPeersHandler;
    protected final BitcoinNode.RequestUnconfirmedTransactionsHandler _requestUnconfirmedTransactionsHandler;
    protected final BitcoinNode.SpvBlockInventoryAnnouncementHandler _spvBlockInventoryAnnouncementHandler;
    protected final BitcoinBinaryPacketFormat _binaryPacketFormat;
    protected final BitcoinNode.NewBloomFilterHandler _newBloomFilterHandler;

    protected void _initializeNode(final BitcoinNode bitcoinNode) {
        bitcoinNode.setSynchronizationStatusHandler(_synchronizationStatus);

        bitcoinNode.setRequestBlockHashesHandler(_requestBlockHashesHandler);
        bitcoinNode.setRequestBlockHeadersHandler(_requestBlockHeadersHandler);
        bitcoinNode.setRequestDataHandler(_requestDataHandler);
        bitcoinNode.setRequestSpvBlocksHandler(_requestSpvBlocksHandler);
        bitcoinNode.setRequestSlpTransactionsHandler(_requestSlpTransactionsHandler);
        bitcoinNode.setSpvBlockInventoryAnnouncementCallback(_spvBlockInventoryAnnouncementHandler);

        bitcoinNode.setBlockInventoryMessageHandler(_blockInventoryAnnouncementHandler);
        bitcoinNode.setRequestUnconfirmedTransactionsHandler(_requestUnconfirmedTransactionsHandler);

        final TransactionsAnnouncementHandlerFactory transactionsAnnouncementHandlerFactory = _transactionsAnnouncementHandlerFactory;
        if (transactionsAnnouncementHandlerFactory != null) {
            final BitcoinNode.TransactionInventoryAnnouncementHandler transactionInventoryAnnouncementHandler = transactionsAnnouncementHandlerFactory.createTransactionsAnnouncementHandler(bitcoinNode);
            bitcoinNode.setTransactionsAnnouncementCallback(transactionInventoryAnnouncementHandler);
        }

        final DoubleSpendProofAnnouncementHandlerFactory doubleSpendProofAnnouncementHandlerFactory = _doubleSpendProofAnnouncementHandlerFactory;
        if (doubleSpendProofAnnouncementHandlerFactory != null) {
            final BitcoinNode.DoubleSpendProofAnnouncementHandler doubleSpendProofAnnouncementHandler = doubleSpendProofAnnouncementHandlerFactory.createDoubleSpendProofAnnouncementHandler(bitcoinNode);
            bitcoinNode.setDoubleSpendProofAnnouncementCallback(doubleSpendProofAnnouncementHandler);
        }

        bitcoinNode.setRequestPeersHandler(_requestPeersHandler);
        bitcoinNode.setNewBloomFilterHandler(_newBloomFilterHandler);
    }

    public NodeInitializer(final Context properties) {
        _synchronizationStatus = properties.synchronizationStatus;
        _blockInventoryAnnouncementHandler = properties.blockInventoryMessageHandler;
        _transactionsAnnouncementHandlerFactory = properties.transactionsAnnouncementHandlerFactory;
        _doubleSpendProofAnnouncementHandlerFactory = properties.doubleSpendProofAnnouncementHandlerFactory;
        _requestBlockHashesHandler = properties.requestBlockHashesHandler;
        _requestBlockHeadersHandler = properties.requestBlockHeadersHandler;
        _requestDataHandler = properties.requestDataHandler;
        _requestSpvBlocksHandler = properties.requestSpvBlocksHandler;
        _requestSlpTransactionsHandler = properties.requestSlpTransactionsHandler;
        _threadPoolFactory = properties.threadPoolFactory;
        _localNodeFeatures = properties.localNodeFeatures;
        _requestPeersHandler = properties.requestPeersHandler;
        _requestUnconfirmedTransactionsHandler = properties.requestUnconfirmedTransactionsHandler;
        _spvBlockInventoryAnnouncementHandler = properties.spvBlockInventoryAnnouncementHandler;
        _binaryPacketFormat = properties.binaryPacketFormat;
        _newBloomFilterHandler = properties.newBloomFilterHandler;
    }

    public void initializeNode(final BitcoinNode bitcoinNode) {
        _initializeNode(bitcoinNode);
    }

    public BitcoinBinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }
}
