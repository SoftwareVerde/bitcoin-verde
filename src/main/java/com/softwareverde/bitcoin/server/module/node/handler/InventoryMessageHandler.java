package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class InventoryMessageHandler implements BitcoinNode.BlockInventoryMessageCallback {
    public static final BitcoinNode.BlockInventoryMessageCallback IGNORE_INVENTORY_HANDLER = new BitcoinNode.BlockInventoryMessageCallback() {
        @Override
        public void onResult(final List<Sha256Hash> result) { }
    };

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected Runnable _newBlockHashesCallback;

    protected Boolean _storeBlockHashes(final List<Sha256Hash> blockHashes) {
        Boolean newBlockHashReceived = false;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseCache);

            for (final Sha256Hash blockHash : blockHashes) {
                // NOTE: The order of the "does-exist" checks matter in order to prevent a race condition between this callback and the BlockchainSynchronizer...
                final Boolean blockExists = blockHeaderDatabaseManager.blockHeaderExists(blockHash);
                if (blockExists) { continue; }
                final Boolean pendingBlockExists = pendingBlockDatabaseManager.pendingBlockExists(blockHash);
                if (pendingBlockExists) { continue; }

                pendingBlockDatabaseManager.storeBlockHash(blockHash);
                newBlockHashReceived = true;
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return newBlockHashReceived;
    }

    public InventoryMessageHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;
    }

    public void setNewBlockHashesCallback(final Runnable newBlockHashesCallback) {
        _newBlockHashesCallback = newBlockHashesCallback;
    }

    @Override
    public void onResult(final List<Sha256Hash> blockHashes) {
        final Boolean newBlockHashReceived = _storeBlockHashes(blockHashes);

        if (newBlockHashReceived) {
            final Runnable newBlockHashesCallback = _newBlockHashesCallback;
            if (newBlockHashesCallback != null) {
                newBlockHashesCallback.run();
            }
        }
    }
}
