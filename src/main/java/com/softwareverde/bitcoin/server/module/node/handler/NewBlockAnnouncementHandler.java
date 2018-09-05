package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
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
    protected final Container<BlockProcessor> _blockProcessor;
    protected final Container<BlockDownloader> _blockDownloader;

    public NewBlockAnnouncementHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final Container<BlockProcessor> blockProcessor, final Container<BlockDownloader> blockDownloader) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _blockProcessor = blockProcessor;
        _blockDownloader = blockDownloader;
    }

    @Override
    public void onResult(final Block block) {
        Logger.log("New Block Announced: " + block.getHash());
        final BlockDownloader blockDownloader = _blockDownloader.value;
        final BlockProcessor blockProcessor = _blockProcessor.value;

        if (blockDownloader.isRunning()) {
            Logger.log("Ignoring new block while syncing: " + block.getHash());
            return;
        }

        final Boolean parentBlockExists;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final Sha256Hash parentBlockHash = block.getPreviousBlockHash();
            final BlockDatabaseManager databaseManager = new BlockDatabaseManager(databaseConnection);
            final BlockId parentBlockId = databaseManager.getBlockIdFromHash(parentBlockHash);
            parentBlockExists = (parentBlockId != null);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return;
        }

        if (parentBlockExists) {
            blockProcessor.processBlock(block);
        }
        else {
            blockDownloader.start();
        }
    }
}