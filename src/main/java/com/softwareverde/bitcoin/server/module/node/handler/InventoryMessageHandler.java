package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class InventoryMessageHandler implements BitcoinNode.InventoryMessageCallback {
    public static final BitcoinNode.InventoryMessageCallback IGNORE_INVENTORY_HANDLER = new BitcoinNode.InventoryMessageCallback() {
        @Override
        public void onResult(final List<Sha256Hash> result) { }
    };

    public static final Object MUTEX = new Object();

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected Runnable _newBlockHashesCallback;

    protected void _storeBlockHashes(final List<Sha256Hash> blockHashes) {
        Boolean newBlockHashReceived = false;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);

            synchronized (MUTEX) {
                for (final Sha256Hash blockHash : blockHashes) {
                    // NOTE: The order of the "does-exist" checks matter in order to prevent a race condition between this callback and the BlockChainSynchronizer...
                    final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(blockHash);
                    if (blockId != null) { continue; }
                    final PendingBlockId existingPendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(blockHash);
                    if (existingPendingBlockId != null) { continue; }

                    pendingBlockDatabaseManager.insertBlockHash(blockHash);
                    newBlockHashReceived = true;
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        if (newBlockHashReceived) {
            final Runnable newBlockHashesCallback = _newBlockHashesCallback;
            if (newBlockHashesCallback != null) {
                newBlockHashesCallback.run();
            }
        }
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
        _storeBlockHashes(blockHashes);
    }
}
