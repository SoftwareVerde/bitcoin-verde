package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.sync.BlockSynchronizer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

public class BlockAnnouncementHandler implements BitcoinNode.BlockAnnouncementCallback {
    public static final BitcoinNode.BlockAnnouncementCallback IGNORE_NEW_BLOCKS_HANDLER = new BitcoinNode.BlockAnnouncementCallback() {
        @Override
        public void onResult(final Block result) { }
    };

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final Container<BlockSynchronizer> _blockDownloader;

    public BlockAnnouncementHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final Container<BlockSynchronizer> blockDownloader) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _blockDownloader = blockDownloader;
    }

    @Override
    public void onResult(final Block block) {
        final Sha256Hash blockHash = block.getHash();

        Logger.log("New Block Announced: " + blockHash);
    }
}