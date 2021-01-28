package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.blockchain.BlockchainMetadata;
import com.softwareverde.bitcoin.server.module.node.rpc.blockchain.BlockchainMetadataBuilder;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;

public class QueryBlockchainHandler implements NodeRpcHandler.QueryBlockchainHandler {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public QueryBlockchainHandler(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    public List<BlockchainMetadata> getBlockchainMetadata() {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockchainMetadataBuilder blockchainMetadataBuilder = new BlockchainMetadataBuilder();

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT blockchain_segment_id, blockchain_segments.nested_set_left, blockchain_segments.nested_set_right, parent_blockchain_segment_id, COUNT(*) AS block_count, MIN(block_height) AS min_block_height, MAX(block_height) AS max_block_height FROM blocks INNER JOIN blockchain_segments ON blockchain_segments.id = blocks.blockchain_segment_id GROUP BY blocks.blockchain_segment_id ORDER BY nested_set_left ASC, nested_set_right ASC")
            );

            final ImmutableListBuilder<BlockchainMetadata> blockchainMetadataList = new ImmutableListBuilder<BlockchainMetadata>(rows.size());

            for (final Row row : rows) {
                final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
                final BlockchainSegmentId parentBlockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("parent_blockchain_segment_id"));

                final Integer nestedSetLeft = row.getInteger("nested_set_left");
                final Integer nestedSetRight = row.getInteger("nested_set_right");

                final Long blockCount = row.getLong("block_count");

                final Long minBlockHeight = row.getLong("min_block_height");
                final Long maxBlockHeight = row.getLong("max_block_height");

                blockchainMetadataBuilder.setBlockchainSegmentId(blockchainSegmentId);
                blockchainMetadataBuilder.setParentBlockchainSegmentId(parentBlockchainSegmentId);
                blockchainMetadataBuilder.setNestedSet(nestedSetLeft, nestedSetRight);
                blockchainMetadataBuilder.setBlockCount(blockCount);
                blockchainMetadataBuilder.setBlockHeight(minBlockHeight, maxBlockHeight);

                final BlockchainMetadata blockchainMetadata = blockchainMetadataBuilder.build();
                if (blockchainMetadata != null) {
                    blockchainMetadataList.add(blockchainMetadata);
                }
            }

            return blockchainMetadataList.build();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }
}
