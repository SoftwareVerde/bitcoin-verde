package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;

public class BlockDownloadRequester {
    protected final MysqlDatabaseConnectionFactory _connectionFactory;
    protected final BlockDownloader _blockDownloader;

    protected void _requestBlock(final Sha256Hash blockHash, final Long priority) {
        if (Util.areEqual(blockHash, Sha256Hash.EMPTY_HASH)) { (new Exception()).printStackTrace(); }

        try (final MysqlDatabaseConnection databaseConnection = _connectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash);
            pendingBlockDatabaseManager.setPriority(pendingBlockId, priority);

            // Logger.log("~~~ BlockDownloadRequester - A");
            _blockDownloader.wakeUp();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    public BlockDownloadRequester(final MysqlDatabaseConnectionFactory connectionFactory, final BlockDownloader blockDownloader) {
        _connectionFactory = connectionFactory;
        _blockDownloader = blockDownloader;
    }

    public void requestBlock(final Sha256Hash blockHash, final Long priority) {
        _requestBlock(blockHash, priority);
    }

    public void requestBlock(final Sha256Hash blockHash) {
        _requestBlock(blockHash, 0L);
    }
}
