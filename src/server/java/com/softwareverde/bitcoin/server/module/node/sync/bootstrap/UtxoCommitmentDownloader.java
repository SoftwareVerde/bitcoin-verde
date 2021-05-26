package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeFilter;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;

public class UtxoCommitmentDownloader {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final List<UtxoCommitmentMetadata> _utxoCommitmentParams;
    protected final BitcoinNodeManager _bitcoinNodeManager;

    protected UtxoCommitmentMetadata _selectUtxoCommitmentToLoad(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
        final Tuple<UtxoCommitmentMetadata, Long> bestCommitmentParam = new Tuple<>(null, 0L);
        for (final UtxoCommitmentMetadata utxoCommitmentParam : _utxoCommitmentParams) {
            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(utxoCommitmentParam.blockHash);
            if (blockId == null) { continue; }

            final boolean isConnectedToMainChain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
            if (! isConnectedToMainChain) { continue; }

            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            if (blockHeight >= bestCommitmentParam.second) {
                bestCommitmentParam.first = utxoCommitmentParam;
                bestCommitmentParam.second = blockHeight;
            }
        }

        return bestCommitmentParam.first;
    }

    public UtxoCommitmentDownloader(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager) {
        _databaseManagerFactory = databaseManagerFactory;
        _utxoCommitmentParams = BitcoinConstants.getUtxoCommitments();
        _bitcoinNodeManager = bitcoinNodeManager;
    }

    public void run() throws DatabaseException {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UtxoCommitmentMetadata utxoCommitmentParam = _selectUtxoCommitmentToLoad(databaseManager);
            if (utxoCommitmentParam == null) {
                Logger.debug("No applicable UtxoCommitment found.");
                return;
            }

            final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes(new NodeFilter() {
                @Override
                public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                    final NodeFeatures nodeFeatures = bitcoinNode.getNodeFeatures();
                    return nodeFeatures.isFeatureEnabled(NodeFeatures.Feature.UTXO_COMMITMENTS_ENABLED);
                }
            });
            if (connectedNodes.isEmpty()) {
                Logger.debug("No peers available with UtxoCommitments.");
                return;
            }


        }
    }
}
