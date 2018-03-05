package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.ChainDatabaseManager;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;

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

        final MutableList<Hash> availableBlockHashes = new MutableList<Hash>();

        final Node.DownloadBlockCallback downloadBlockCallback = new Node.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                Logger.log("DOWNLOADED BLOCK: "+ BitcoinUtil.toHexString(block.getHash()));

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
                        Logger.log("Invalid block: "+ block.getHash());
                        _exitFailure();
                    }

                    TransactionUtil.commitTransaction(databaseConnection);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
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

    protected void _promptToDownloadAllBlocks(final Node node) {
        System.out.println("Press any key to download the Blockchain...");
        try { (new BufferedInputStream(System.in)).read(); } catch (final IOException exception) { }
        _downloadAllBlocks(node);
    }

    public Main(final String[] commandLineArguments) {

        /*
            { // Create Private/Public Key:
                final BitcoinPrivateKey privateKey = BitcoinPrivateKey.createNewKey();
                System.out.println("Private Key: " + BitcoinUtil.toHexString(privateKey.getBytes()));
                System.out.println("Public Key: " + BitcoinUtil.toBase58String(privateKey.getBitcoinAddress()));
                _exitFailure();

                // Private Key: CE418F2262D69CA2E02645E679598F3F646E8158BA7C5890A67130390A1102E5
                // Public Key: 13TXBs1AonKbypUZCRYRCFnuLppqm69odd
            }
        */

        // Mine Hardcoded Block...
        {
            try {
                final BlockInflater blockInflater = new BlockInflater();

                final Block previousBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));

                final MutableBlock prototypeBlock = new MutableBlock();
                {
                    final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                    mutableTransactionInput.setPreviousTransactionOutputHash(new MutableHash());
                    mutableTransactionInput.setPreviousTransactionOutputIndex(0);
                    mutableTransactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
                    mutableTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").build());

                    final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                    mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
                    mutableTransactionOutput.setIndex(0);
                    mutableTransactionOutput.setLockingScript((ScriptBuilder.payToAddress("13TXBs1AonKbypUZCRYRCFnuLppqm69odd")));

                    final MutableTransaction coinbaseTransaction = new MutableTransaction();
                    coinbaseTransaction.setVersion(1);
                    coinbaseTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
                    coinbaseTransaction.setHasWitnessData(false);
                    coinbaseTransaction.addTransactionInput(mutableTransactionInput);
                    coinbaseTransaction.addTransactionOutput(mutableTransactionOutput);

                    // Logger.log(BitcoinUtil.toHexString(coinbaseTransaction.getBytes()));
                    // _exitFailure();

                    prototypeBlock.setVersion(1);
                    prototypeBlock.setPreviousBlockHash(previousBlock.getHash());
                    prototypeBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                    prototypeBlock.setNonce(0L);
                    prototypeBlock.setDifficulty(new ImmutableDifficulty(ByteUtil.integerToBytes(Difficulty.BASE_DIFFICULTY_SIGNIFICAND), Difficulty.BASE_DIFFICULTY_EXPONENT));
                    prototypeBlock.addTransaction(coinbaseTransaction);
                }

                {
                    final BlockHeader blockHeader = prototypeBlock.asConst();
                    final ImmutableBlockHeader immutableBlockHeader = blockHeader.asConst();
                    immutableBlockHeader.asConst();
                }

                final Miner miner = new Miner();
                miner.mineBlock(previousBlock, prototypeBlock);
            }
            catch (final Exception exception) {
                exception.printStackTrace();
            }
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
                            Logger.log("Invalid block: "+ block.getHash());
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