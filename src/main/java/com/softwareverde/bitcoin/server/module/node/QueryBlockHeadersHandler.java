package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.type.query.block.header.QueryBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashType;
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

public class QueryBlockHeadersHandler implements BitcoinNode.QueryBlockHeadersCallback {

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public QueryBlockHeadersHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    protected void _addChildrenBlocks(final BlockId blockId, final BlockChainSegmentId blockChainSegmentId, final BlockDatabaseManager blockDatabaseManager, final MutableList<Sha256Hash> returnedBlockHashes) throws DatabaseException {
        BlockId nextBlockId = blockId;
        while (true) {
            final Sha256Hash addedBlockHash = blockDatabaseManager.getBlockHashFromId(nextBlockId);
            if (addedBlockHash == null) { break; }
            returnedBlockHashes.add(addedBlockHash);

            if (returnedBlockHashes.getSize() >= QueryBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) {
                break;
            }

            nextBlockId = blockDatabaseManager.getChildBlockId(blockChainSegmentId, nextBlockId);
            if (nextBlockId == null) { break; }
        }
    }

    @Override
    public void run(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final NodeConnection nodeConnection) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final BlockId desiredBlockId = blockDatabaseManager.getBlockIdFromHash(desiredBlockHash);
            final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(desiredBlockId);

            final MutableList<Sha256Hash> returnedBlockHashes = new MutableList<Sha256Hash>();
            for (final Sha256Hash blockHash : blockHashes) {

                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id FROM blocks WHERE hash = ?")
                        .setParameter(blockHash)
                );
                if (rows.isEmpty()) { continue; }

                final BlockId blockId = BlockId.wrap(rows.get(0).getLong("id"));
                if (blockId != null) {
                    _addChildrenBlocks(blockId, blockChainSegmentId, blockDatabaseManager, returnedBlockHashes);
                    break;
                }
            }

            if (returnedBlockHashes.isEmpty()) {
                final Sha256Hash headBlockHash = blockDatabaseManager.getHeadBlockHash();
                final BlockId headBlockHashId = blockDatabaseManager.getBlockIdFromHash(headBlockHash);
                final BlockChainSegmentId bestBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(headBlockHashId);

                final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(Block.GENESIS_BLOCK_HEADER_HASH);
                _addChildrenBlocks(blockId, bestBlockChainSegmentId, blockDatabaseManager, returnedBlockHashes);
            }

            final QueryResponseMessage queryResponseMessage = new QueryResponseMessage();
            for (final Sha256Hash blockHash : returnedBlockHashes) {
                queryResponseMessage.addInventoryItem(new DataHash(DataHashType.BLOCK, blockHash));
            }
            nodeConnection.queueMessage(queryResponseMessage);
        }
        catch (final DatabaseException exception) { Logger.log(exception); }
    }
}
