package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class RequestBlockHeadersHandler extends AbstractRequestBlocksHandler implements BitcoinNode.RequestBlockHeadersHandler {
    public static final BitcoinNode.RequestBlockHeadersHandler IGNORES_REQUESTS_HANDLER = new BitcoinNode.RequestBlockHeadersHandler() {
        @Override
        public void run(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash) { }
    };

    protected final DatabaseManagerFactory _databaseManagerFactory;

    public RequestBlockHeadersHandler(final DatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final StartingBlock startingBlock = _getStartingBlock(blockHashes, false, desiredBlockHash, databaseManager);

            if (startingBlock == null) {
                Logger.debug("Unable to send headers: No blocks available.");
                return;
            }

            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final Sha256Hash batchContinueHash = bitcoinNode.getBatchContinueHash();
            boolean sendBatchContinueInventory = false;

            Sha256Hash lastBlockHeaderHash = null;
            final MutableList<BlockHeader> blockHeaders = new MutableList<BlockHeader>();
            {
                final List<BlockId> childrenBlockIds = _findBlockChildrenIds(startingBlock.startingBlockId, desiredBlockHash, startingBlock.selectedBlockchainSegmentId, RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, databaseManager);
                for (final BlockId blockId : childrenBlockIds) {
                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    final Sha256Hash blockHash = blockHeader.getHash();

                    blockHeaders.add(blockHeader);
                    lastBlockHeaderHash = blockHash;

                    if (Util.areEqual(batchContinueHash, blockHash)) {
                        sendBatchContinueInventory = true;
                    }
                }
            }

            if (sendBatchContinueInventory) {
                final Sha256Hash headBlockHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();
                bitcoinNode.transmitBatchContinueHash(headBlockHash);
            }

            if (lastBlockHeaderHash != null) {
                bitcoinNode.setBatchContinueHash(lastBlockHeaderHash);
            }

            bitcoinNode.transmitBlockHeaders(blockHeaders);
        }
        catch (final Exception exception) { Logger.warn(exception); }
    }
}
