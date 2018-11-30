package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class BlockDownloadRequester {
    protected final SystemTime _systemTime = new SystemTime();
    protected final MysqlDatabaseConnectionFactory _connectionFactory;
    protected final BlockDownloader _blockDownloader;
    protected final BitcoinNodeManager _nodeManager;
    protected final DatabaseManagerCache _databaseCache;

    protected Tuple<Sha256Hash, Long> _lastUnavailableRequestedBlock = new Tuple<Sha256Hash, Long>(Sha256Hash.EMPTY_HASH, 0L);

    protected void _requestBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash, final Long priority) {
        try (final MysqlDatabaseConnection databaseConnection = _connectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash, previousBlockHash);
            pendingBlockDatabaseManager.setPriority(pendingBlockId, priority);

            { // Check if any peers have the requested block...
                // If none of the nodes have the block in their known inventory, ask the peers specifically for the block.
                // If no peers still do not have the block, search for a historic node with the block.

                final Long now = _systemTime.getCurrentTimeInSeconds();
                final Long durationSinceLastRequest = (now - _lastUnavailableRequestedBlock.second);
                if ( (! Util.areEqual(blockHash, _lastUnavailableRequestedBlock.first)) || durationSinceLastRequest > 10L) {

                    final List<NodeId> connectedNodes = _nodeManager.getNodeIds();
                    final Boolean nodesHaveInventory = pendingBlockDatabaseManager.nodesHaveBlockInventory(connectedNodes, blockHash);
                    if (! nodesHaveInventory) {
                        final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseConnection, _databaseCache);
                        final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
                        _nodeManager.broadcastBlockFinder(blockFinderHashes);

                        _lastUnavailableRequestedBlock.first = blockHash;
                        _lastUnavailableRequestedBlock.second = now;
                    }
                }
            }

            _blockDownloader.wakeUp();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    public BlockDownloadRequester(final MysqlDatabaseConnectionFactory connectionFactory, final BlockDownloader blockDownloader, final BitcoinNodeManager bitcoinNodeManager, final DatabaseManagerCache databaseManagerCache) {
        _connectionFactory = connectionFactory;
        _blockDownloader = blockDownloader;
        _nodeManager = bitcoinNodeManager;
        _databaseCache = databaseManagerCache;
    }

    public void requestBlock(final BlockHeader blockHeader) {
        _requestBlock(blockHeader.getHash(), blockHeader.getPreviousBlockHash(), blockHeader.getTimestamp());
    }

    public void requestBlock(final Sha256Hash blockHash, final Long priority) {
        _requestBlock(blockHash, null, priority);
    }

    public void requestBlock(final Sha256Hash blockHash) {
        _requestBlock(blockHash, null, 0L);
    }
}
