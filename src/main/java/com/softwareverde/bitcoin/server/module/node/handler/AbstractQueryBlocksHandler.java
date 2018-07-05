package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.type.query.block.header.QueryBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.header.QueryBlockHeadersResponseMessage;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeConnection;

public class AbstractQueryBlocksHandler implements BitcoinNode.QueryBlockHeadersCallback {
    protected static class StartingBlock {
        public final BlockChainSegmentId selectedBlockChainSegmentId;
        public final BlockId startingBlockId;

        public StartingBlock(final BlockChainSegmentId blockChainSegmentId, final BlockId startingBlockId) {
            this.selectedBlockChainSegmentId = blockChainSegmentId;
            this.startingBlockId = startingBlockId;
        }
    }

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    protected AbstractQueryBlocksHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    protected List<BlockId> _findBlockChildrenIds(final BlockId blockId, final Sha256Hash desiredBlockHash, final BlockChainSegmentId blockChainSegmentId, final BlockDatabaseManager blockDatabaseManager) throws DatabaseException {
        final MutableList<BlockId> returnedBlockIds = new MutableList<BlockId>();

        BlockId nextBlockId = blockId;
        while (true) {
            final Sha256Hash addedBlockHash = blockDatabaseManager.getBlockHashFromId(nextBlockId);
            if (addedBlockHash == null) { break; }

            returnedBlockIds.add(nextBlockId);

            if (addedBlockHash.equals(desiredBlockHash)) { break; }
            if (returnedBlockIds.getSize() >= QueryBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { break; }

            nextBlockId = blockDatabaseManager.getChildBlockId(blockChainSegmentId, nextBlockId);
            if (nextBlockId == null) { break; }
        }

        return returnedBlockIds;
    }

    protected StartingBlock _getStartingBlock(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final BlockChainSegmentId blockChainSegmentId;
        final BlockId startingBlockId;
        {
            BlockId foundBlockId = null;
            for (final Sha256Hash blockHash : blockHashes) {
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id FROM blocks WHERE hash = ?")
                        .setParameter(blockHash)
                );
                if (rows.isEmpty()) {
                    continue;
                }

                final BlockId blockId = BlockId.wrap(rows.get(0).getLong("id"));
                if (blockId != null) {
                    foundBlockId = blockId;
                    break;
                }
            }

            if (foundBlockId == null) {
                final Sha256Hash headBlockHash = blockDatabaseManager.getHeadBlockHash();
                if (headBlockHash != null) {
                    final BlockId genesisBlockId = blockDatabaseManager.getBlockIdFromHash(Block.GENESIS_BLOCK_HASH);
                    foundBlockId = genesisBlockId;

                    final BlockId headBlockHashId = blockDatabaseManager.getBlockIdFromHash(headBlockHash);
                    blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(headBlockHashId);
                }
                else {
                    foundBlockId = null;
                    blockChainSegmentId = null;
                }
            }
            else {
                final BlockId desiredBlockId = blockDatabaseManager.getBlockIdFromHash(desiredBlockHash);
                blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(desiredBlockId);
            }

            startingBlockId = foundBlockId;
        }

        if (blockChainSegmentId == null || startingBlockId == null) { return null; }
        return new StartingBlock(blockChainSegmentId, startingBlockId);
    }

    @Override
    public void run(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final NodeConnection nodeConnection) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final StartingBlock startingBlock = _getStartingBlock(blockHashes, desiredBlockHash, databaseConnection);

            if (startingBlock == null) {
                Logger.log("Unable to send headers: No blocks available.");
                return;
            }

            final MutableList<BlockHeaderWithTransactionCount> blockHeaders = new MutableList<BlockHeaderWithTransactionCount>();
            {
                final List<BlockId> childrenBlockIds = _findBlockChildrenIds(startingBlock.startingBlockId, desiredBlockHash, startingBlock.selectedBlockChainSegmentId, blockDatabaseManager);
                for (final BlockId blockId : childrenBlockIds) {
                    final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
                    final Integer transactionCount = blockDatabaseManager.getTransactionCount(blockId);
                    final BlockHeaderWithTransactionCount blockHeaderWithTransactionCount = new ImmutableBlockHeaderWithTransactionCount(blockHeader, transactionCount);
                    blockHeaders.add(blockHeaderWithTransactionCount);
                }
            }

            final QueryBlockHeadersResponseMessage responseMessage = new QueryBlockHeadersResponseMessage();
            for (final BlockHeaderWithTransactionCount blockHeader : blockHeaders) {
                responseMessage.addBlockHeader(blockHeader);
            }

            nodeConnection.queueMessage(responseMessage);
        }
        catch (final DatabaseException exception) { Logger.log(exception); }
    }
}
