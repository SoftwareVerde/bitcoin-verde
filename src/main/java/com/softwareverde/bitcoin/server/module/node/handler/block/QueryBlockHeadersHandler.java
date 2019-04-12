package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;

public class QueryBlockHeadersHandler extends AbstractQueryBlocksHandler implements BitcoinNode.QueryBlockHeadersCallback {
    public static final BitcoinNode.QueryBlockHeadersCallback IGNORES_REQUESTS_HANDLER = new BitcoinNode.QueryBlockHeadersCallback() {
        @Override
        public void run(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final BitcoinNode bitcoinNode) { }
    };

    public QueryBlockHeadersHandler(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        super(databaseConnectionFactory, databaseManagerCache);
    }

    @Override
    public void run(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final BitcoinNode bitcoinNode) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final StartingBlock startingBlock = _getStartingBlock(blockHashes, false, desiredBlockHash, databaseConnection);

            if (startingBlock == null) {
                Logger.log("Unable to send headers: No blocks available.");
                return;
            }

            final MutableList<BlockHeader> blockHeaders = new MutableList<BlockHeader>();
            {
                final List<BlockId> childrenBlockIds = _findBlockChildrenIds(startingBlock.startingBlockId, desiredBlockHash, startingBlock.selectedBlockchainSegmentId, RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, blockHeaderDatabaseManager);
                for (final BlockId blockId : childrenBlockIds) {
                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    blockHeaders.add(blockHeader);
                }
            }

            bitcoinNode.transmitBlockHeaders(blockHeaders);
        }
        catch (final Exception exception) { Logger.log(exception); }
    }
}
