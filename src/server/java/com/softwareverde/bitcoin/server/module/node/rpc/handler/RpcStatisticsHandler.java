package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.request.UnfulfilledPublicKeyRequest;
import com.softwareverde.bitcoin.server.node.request.UnfulfilledSha256HashRequest;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class RpcStatisticsHandler implements NodeRpcHandler.StatisticsHandler {
    protected final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockchainBuilder _blockchainBuilder;
    protected final BlockProcessor _blockProcessor;
    protected final BitcoinNodeManager _bitcoinNodeManager;

    public RpcStatisticsHandler(final BlockHeaderDownloader blockHeaderDownloader, final BlockchainBuilder blockchainBuilder, final BlockProcessor blockProcessor, final BitcoinNodeManager bitcoinNodeManager) {
        _blockHeaderDownloader = blockHeaderDownloader;
        _blockchainBuilder = blockchainBuilder;
        _blockProcessor = blockProcessor;
        _bitcoinNodeManager = bitcoinNodeManager;
    }

    @Override
    public Float getAverageBlockHeadersPerSecond() {
        return _blockHeaderDownloader.getAverageBlockHeadersPerSecond();
    }

    @Override
    public Float getAverageBlocksPerSecond() {
        return _blockchainBuilder.getAverageBlocksPerSecond();
    }

    @Override
    public Float getAverageTransactionsPerSecond() {
        return _blockProcessor.getAverageTransactionsPerSecond();
    }

    @Override
    public List<UnfulfilledSha256HashRequest> getActiveBlockDownloads() {
        final MutableList<UnfulfilledSha256HashRequest> pendingBlockDownloads = new MutableList<>(0);

        final List<BitcoinNode> bitcoinNodes = _bitcoinNodeManager.getNodes();
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            final List<UnfulfilledSha256HashRequest> pendingDownloads = bitcoinNode.getPendingBlockRequests();
            pendingBlockDownloads.addAll(pendingDownloads);
        }

        return pendingBlockDownloads;
    }

    @Override
    public List<UnfulfilledPublicKeyRequest> getActiveUtxoCommitmentDownloads() {
        final MutableList<UnfulfilledPublicKeyRequest> pendingUtxoCommitmentDownloads = new MutableList<>(0);

        final List<BitcoinNode> bitcoinNodes = _bitcoinNodeManager.getNodes();
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            final List<UnfulfilledPublicKeyRequest> pendingDownloads = bitcoinNode.getPendingUtxoCommitmentRequests();
            pendingUtxoCommitmentDownloads.addAll(pendingDownloads);
        }

        return pendingUtxoCommitmentDownloads;
    }

    @Override
    public List<UnfulfilledSha256HashRequest> getActiveTransactionDownloads() {
        final MutableList<UnfulfilledSha256HashRequest> pendingTransactionDownloads = new MutableList<>(0);

        final List<BitcoinNode> bitcoinNodes = _bitcoinNodeManager.getNodes();
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            final List<UnfulfilledSha256HashRequest> pendingDownloads = bitcoinNode.getPendingTransactionRequests();
            pendingTransactionDownloads.addAll(pendingDownloads);
        }

        return pendingTransactionDownloads;
    }
}
