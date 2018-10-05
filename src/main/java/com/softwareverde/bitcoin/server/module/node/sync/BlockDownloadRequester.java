package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class BlockDownloadRequester {
    protected final MysqlDatabaseConnectionFactory _connectionFactory;
    protected final BlockDownloader _blockDownloader;

    protected void _requestBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash, final Long priority) {
        try (final MysqlDatabaseConnection databaseConnection = _connectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash, previousBlockHash);
            pendingBlockDatabaseManager.setPriority(pendingBlockId, priority);

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
