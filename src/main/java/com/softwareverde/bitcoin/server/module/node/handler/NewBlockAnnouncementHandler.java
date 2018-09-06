package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockDownloader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

public class NewBlockAnnouncementHandler implements BitcoinNode.NewBlockAnnouncementCallback {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final Container<BlockDownloader> _blockDownloader;

    public NewBlockAnnouncementHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final Container<BlockDownloader> blockDownloader) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _blockDownloader = blockDownloader;
    }

    @Override
    public void onResult(final Block block) {
        final Sha256Hash blockHash = block.getHash();

        Logger.log("New Block Announced: " + blockHash);
        final BlockDownloader blockDownloader = _blockDownloader.value;

        if (blockDownloader.isRunning()) {
            Logger.log("Ignoring new block while syncing: " + blockHash);
            return;
        }

        final Boolean parentBlockExists;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final Boolean blockIsSynchronized = blockDatabaseManager.isBlockSynchronized(blockHash);
            if (blockIsSynchronized) {
                Logger.log("Already aware of announced block: " + blockHash);
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
            blockDownloader.submitBlock(block);
        }
        else {
            blockDownloader.start();
        }
    }
}