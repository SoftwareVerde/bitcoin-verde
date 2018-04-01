package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.network.NetworkTime;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.HexUtil;

import java.io.File;

public class NodeModule {

    protected final Configuration _configuration;
    protected final Environment _environment;
    protected final NetworkTime _networkTime;

    protected long _startTime;
    protected int _blockCount = 0;
    protected int _transactionCount = 0;

    protected void _exitFailure() {
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            _exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected void _downloadAllBlocks(final Node node) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();

        final Hash resumeAfterHash;
        {
            Hash lastKnownHash = null;
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                lastKnownHash = blockDatabaseManager.getMostRecentBlockHash();
            }
            catch (final DatabaseException e) { }

            resumeAfterHash = ((lastKnownHash == null) ? Block.GENESIS_BLOCK_HEADER_HASH : lastKnownHash);
        }

        final Container<Hash> lastBlockHash = new Container<Hash>(resumeAfterHash);
        final Container<Node.QueryCallback> getBlocksHashesAfterCallback = new Container<Node.QueryCallback>();

        final MutableList<Hash> availableBlockHashes = new MutableList<Hash>();

        final Node.DownloadBlockCallback downloadBlockCallback = new Node.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                Logger.log("DOWNLOADED BLOCK: "+ HexUtil.toHexString(block.getHash().getBytes()));

                if (! lastBlockHash.value.equals(block.getPreviousBlockHash())) { return; } // Ignore blocks sent out of order...

                final Boolean isValidBlock = _processBlock(block);

                if (! isValidBlock) {
                    Logger.log("Invalid block: "+ block.getHash());
                    _exitFailure();
                }

                lastBlockHash.value = block.getHash();

                if (! availableBlockHashes.isEmpty()) {
                    node.requestBlock(availableBlockHashes.remove(0), this);
                }
                else {
                    node.getBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
                }
            }
        };

        getBlocksHashesAfterCallback.value = new Node.QueryCallback() {
            @Override
            public void onResult(final java.util.List<Hash> blockHashes) {
                final List<Hash> hashes = new ImmutableList<Hash>(blockHashes); // TODO: Remove the conversion requirement.
                availableBlockHashes.addAll(hashes);

                if (! availableBlockHashes.isEmpty()) {
                    node.requestBlock(availableBlockHashes.remove(0), downloadBlockCallback);
                }
            }
        };

        node.getBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
    }

//    protected void _promptToDownloadAllBlocks(final Node node) {
//        System.out.println("Press any key to download the Blockchain...");
//        try { (new BufferedInputStream(System.in)).read(); } catch (final IOException exception) { }
//        _downloadAllBlocks(node);
//    }

    protected Boolean _processBlock(final Block block) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();

        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            TransactionUtil.startTransaction(databaseConnection);

            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final BlockId blockId = blockDatabaseManager.storeBlock(block);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
            final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

            final ReadUncommittedDatabaseConnectionFactory connectionFactory = new ReadUncommittedDatabaseConnectionFactory(database.getDatabaseConnectionFactory());
            final BlockValidator blockValidator = new BlockValidator(connectionFactory);
            final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);

            if (blockIsValid) {
                TransactionUtil.commitTransaction(databaseConnection);
                _blockCount += 1;
                _transactionCount += block.getTransactions().getSize();

                final long msElapsed = (System.currentTimeMillis() - _startTime);
                Logger.log("Processed "+ _transactionCount + " transactions in " + msElapsed +" ms. (" + String.format("%.2f", ((((double) _transactionCount) / msElapsed) * 1000)) + " tps)");
                Logger.log("Processed "+ _blockCount + " blocks in " + msElapsed +" ms. (" + String.format("%.2f", ((((double) _blockCount) / msElapsed) * 1000)) + " bps)");

                return true;
            }
            else {
                TransactionUtil.rollbackTransaction(databaseConnection);
            }
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }

        Logger.log("Invalid block: "+ block.getHash());
        return false;
    }

    public NodeModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        final Configuration.DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();

        final EmbeddedMysqlDatabase database;
        {
            EmbeddedMysqlDatabase databaseInstance = null;
            try {
                databaseInstance = new EmbeddedMysqlDatabase(databaseProperties);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                _exitFailure();
            }
            database = databaseInstance;
            Logger.log("[Database Online]");
        }

        _environment = new Environment(database);
        _networkTime = new NetworkTime();
    }

    public void loop() {
        Logger.log("[Server Online]");

        final String host = "btc.softwareverde.com";
        final Integer port = 8333;

        final Node node = new Node(host, port);

        final EmbeddedMysqlDatabase database = _environment.getDatabase();

        _startTime = System.currentTimeMillis();

        final Boolean hasGenesisBlock;
        {
            Hash lastKnownHash = null;
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                lastKnownHash = blockDatabaseManager.getMostRecentBlockHash();
            }
            catch (final DatabaseException e) { }
            hasGenesisBlock = (lastKnownHash != null);
        }

        if (! hasGenesisBlock) {
            node.requestBlock(Block.GENESIS_BLOCK_HEADER_HASH, new Node.DownloadBlockCallback() {
                @Override
                public void onResult(final Block block) {
                    final Boolean isValidBlock = _processBlock(block);

                    _downloadAllBlocks(node);
                }
            });
        }
        else {
            _downloadAllBlocks(node);
        }

        while (true) {
            try { Thread.sleep(5000); } catch (final Exception e) { }
        }
    }

    public static void execute(final String configurationFileName) {
        final NodeModule nodeModule = new NodeModule(configurationFileName);
        nodeModule.loop();
    }
}