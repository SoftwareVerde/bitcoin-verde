package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.ChainDatabaseManager;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {


    protected final Configuration _configuration;
    protected final Environment _environment;

    protected void _exitFailure() {
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected void _printUsage() {
        _printError("Usage: java -jar " + System.getProperty("java.class.path") + " <configuration-file>");
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

        final List<Hash> availableBlockHashes = new ArrayList<Hash>();

        final Node.DownloadBlockCallback downloadBlockCallback = new Node.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                Logger.log("DOWNLOADED BLOCK: "+ BitcoinUtil.toHexString(block.calculateSha256Hash()));

                if (! lastBlockHash.value.equals(block.getPreviousBlockHash())) { return; } // Ignore blocks sent out of order...
                try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                    TransactionUtil.startTransaction(databaseConnection);

                    final BlockValidator blockValidator = new BlockValidator(databaseConnection);
                    final Boolean blockIsValid = blockValidator.validateBlock(block);

                    if (blockIsValid) {
                        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                        blockDatabaseManager.storeBlock(block);
                    }
                    else {
                        Logger.log("Invalid block: "+ block.calculateSha256Hash());
                        _exitFailure();
                    }

                    TransactionUtil.commitTransaction(databaseConnection);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    _exitFailure();
                }

                lastBlockHash.value = block.calculateSha256Hash();

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
            public void onResult(final List<Hash> blockHashes) {
                availableBlockHashes.addAll(blockHashes);

                if (! availableBlockHashes.isEmpty()) {
                    node.requestBlock(availableBlockHashes.remove(0), downloadBlockCallback);
                }
            }
        };

        node.getBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
    }

    protected void _promptToDownloadAllBlocks(final Node node) {
        System.out.println("Press any key to download the Blockchain...");
        try { (new BufferedInputStream(System.in)).read(); } catch (final IOException exception) { }
        _downloadAllBlocks(node);
    }

    public Main(final String[] commandLineArguments) {

try {
    final Miner miner = new Miner();
    miner.mineFakeBlock();
    _exitFailure();
}
catch (final Exception exception) {
    exception.printStackTrace();
    _exitFailure();
}

        if (commandLineArguments.length != 1) {
            _printUsage();
            _exitFailure();
        }

        final String configurationFilename = commandLineArguments[0];

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
    }

    public void loop() {
        Logger.log("[Server Online]");

        final String host = "btc.softwareverde.com";
        final Integer port = 8333;

        final Node node = new Node(host, port);

        final EmbeddedMysqlDatabase database = _environment.getDatabase();

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
                    try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                        TransactionUtil.startTransaction(databaseConnection);

                        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);
                        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

                        blockDatabaseManager.storeBlock(block);
                        chainDatabaseManager.updateBlockChainsForNewBlock(block);

                        final BlockValidator blockValidator = new BlockValidator(databaseConnection);
                        final Boolean blockIsValid = blockValidator.validateBlock(block);

                        if (blockIsValid) {
                            TransactionUtil.commitTransaction(databaseConnection);
                        }
                        else {
                            Logger.log("Invalid block: "+ block.calculateSha256Hash());
                            TransactionUtil.rollbackTransaction(databaseConnection);
                        }
                    }
                    catch (final DatabaseException exception) { }

                    _promptToDownloadAllBlocks(node);
                }
            });
        }
        else {
            _promptToDownloadAllBlocks(node);
        }

        while (true) {
            try { Thread.sleep(500); } catch (final Exception e) { }
        }
    }

    public static void main(final String[] commandLineArguments) {
        final Main application = new Main(commandLineArguments);
        application.loop();
    }
}