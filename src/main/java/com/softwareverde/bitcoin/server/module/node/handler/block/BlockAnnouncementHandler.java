package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockSynchronizer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

public class BlockAnnouncementHandler implements BitcoinNode.BlockAnnouncementCallback {
    public static final BitcoinNode.BlockAnnouncementCallback IGNORE_NEW_BLOCKS_HANDLER = new BitcoinNode.BlockAnnouncementCallback() {
        @Override
        public void onResult(final Block result) { }
    };

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final Container<BlockSynchronizer> _blockDownloader;

    public BlockAnnouncementHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final Container<BlockSynchronizer> blockDownloader) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _blockDownloader = blockDownloader;
    }

    @Override
    public void onResult(final Block block) {
        final Sha256Hash blockHash = block.getHash();

        final BlockSynchronizer blockSynchronizer = _blockDownloader.value;

        if (blockSynchronizer.isRunning()) {
            Logger.log("Ignoring new block while syncing: " + blockHash);
            return;
        }

        final Boolean parentBlockExists;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final Boolean blockIsSynchronized = blockDatabaseManager.isBlockSynchronized(blockHash);
            if (blockIsSynchronized) {
                return;
            }

            final Sha256Hash parentBlockHash = block.getPreviousBlockHash();
            final BlockId parentBlockId = blockDatabaseManager.getBlockIdFromHash(parentBlockHash);
            parentBlockExists = (parentBlockId != null);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return;
        }

        if (parentBlockExists) {
            Logger.log("New Block Announced: " + blockHash);
            blockSynchronizer.submitBlock(block);
        }
        else {
            Logger.log("New (Orphaned) Block Announced: " + blockHash + " (Restarting blockSynchronizer...)");
            blockSynchronizer.start();
        }
    }
}