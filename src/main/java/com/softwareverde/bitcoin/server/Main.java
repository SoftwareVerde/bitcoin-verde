package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
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
                    final Boolean blockIsValid = blockValidator.validateBlock(null, block);

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
//        final GpuSha256 gpuSha256 = new GpuSha256();
//
//        final MutableList<ByteArray> items = new MutableList<ByteArray>();
//        for (int i=0; i<32; ++i) {
//            items.add(new ImmutableByteArray(new byte[]{ 0x00 }));
//        }
//        gpuSha256.sha256(gpuSha256.sha256(items));
//        _exitFailure();
//
//        final long begin = System.currentTimeMillis();
//        int count = 0;
//        for (int j=0; j<1024*5; ++j) {
//            final MutableList<ByteArray> inputs = new MutableList<ByteArray>();
//            for (int i=0; i<1024; ++i) {
//                inputs.add(new ImmutableByteArray(new byte[]{ (byte) i }));
//            }
//
//            // final List<Hash> hashes = gpuSha256.sha256(gpuSha256.sha256(inputs));
//            final List<Hash> hashes = gpuSha256.sha256(inputs);
//
////            for (final Hash hash : hashes) {
////                System.out.println(BitcoinUtil.toHexString(hash));
////            }
//
//            count += hashes.getSize();
//        }
//        final long end = System.currentTimeMillis();
//        System.out.println((count / (end - begin) * 1000) + " h/s");
//        _exitFailure();


//        { // Create Private/Public Key:
//            final PrivateKey privateKey = PrivateKey.createNewKey();
//            System.out.println("Private Key: " + BitcoinUtil.toHexString(privateKey.getBytes()));
//            System.out.println("Public Key: " + privateKey.getBitcoinAddress());
//            _exitFailure();
//
//            // Private Key: CE418F2262D69CA2E02645E679598F3F646E8158BA7C5890A67130390A1102E5
//            // Public Key: 13TXBs1AonKbypUZCRYRCFnuLppqm69odd
//
//            // Private Key: D4BF010D3EC25F913CFF91CA34FD4C04A38908E0478B31F669B647EFCD2482A5
//            // Public Key: 1BpgWv8MfioK6UNjfad8NzYVvLeKGfwhwj
//
//            // Private Key: B6AA8D327D94F746EFB1974E151CA405D4C17EAB4AB4F5CB7757B720D9E62280
//            // Public Key: 1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW
//        }

        // Mine Hardcoded Block...
        {
            try {
                final BlockInflater blockInflater = new BlockInflater();

                final Block previousBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D61900000000007DCE47CEB8FC469369F70F2BAEDF22B0377B691FBDF8426E367202FD021A58D2F4569E5AFFFF001D80CFE82A01010000000100000000000000000000000000000000000000000000000000000000000000000000000019184D696E65642076696120426974636F696E2D56657264652EFFFFFFFF0100F2052A010000001E76A619001AF4440149EF4E3936D27C9F54A2AA4EC4F884E14F6B7D5488AC00000000"));

                final TransactionInflater transactionInflater = new TransactionInflater();
                final Transaction transactionWithinBlock1DoublePrime = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000001736EF73E0AF54C6E0205824DF02F8217930B2E3F436E05192754DEEC0287CE75000000008B483045022100D88F161B7B0774AAB84BA0343B572770FC4E4DC902E9CDDD8D1172C591BDF26F022003AE66DB858F2B5ACE01566E21A1F5A6C0FA4D7AAB7AFBD5BC3057D305931ABA0141044B1F57CD308E8AE8AADA38AA8183A348E1F12C107898760A11324E1B0288C49DE1AB5E1DA16F649B7375046E165FD2CF143F08989F1092A7ED6FED183107B236FFFFFFFF0100F2052A010000001976A914B8E012A1EC221C31F69AA2895129C02C90AAE2C588AC00000000"));

                final PrivateKey privateKey = PrivateKey.parseFromHexString("B6AA8D327D94F746EFB1974E151CA405D4C17EAB4AB4F5CB7757B720D9E62280");

                final MutableBlock prototypeBlock = new MutableBlock();
                {
                    final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
                    final MutableTransactionOutput coinbaseTransactionOutput = new MutableTransactionOutput();
                    final MutableTransaction coinbaseTransaction = new MutableTransaction();
                    {
                        coinbaseTransactionInput.setPreviousOutputTransactionHash(new MutableHash());
                        coinbaseTransactionInput.setPreviousOutputIndex(0);
                        coinbaseTransactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
                        coinbaseTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").buildUnlockingScript());

                        coinbaseTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
                        coinbaseTransactionOutput.setIndex(0);
                        coinbaseTransactionOutput.setLockingScript((ScriptBuilder.payToAddress(Address.fromPrivateKey(privateKey))));

                        coinbaseTransaction.setVersion(1);
                        coinbaseTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
                        coinbaseTransaction.setHasWitnessData(false);
                        coinbaseTransaction.addTransactionInput(coinbaseTransactionInput);
                        coinbaseTransaction.addTransactionOutput(coinbaseTransactionOutput);
                    }

                    final MutableTransactionInput newTransactionInput = new MutableTransactionInput();
                    final MutableTransactionOutput newTransactionOutput = new MutableTransactionOutput();
                    final MutableTransaction newTransaction = new MutableTransaction();
                    {
                        newTransactionInput.setPreviousOutputTransactionHash(transactionWithinBlock1DoublePrime.getHash());
                        newTransactionInput.setPreviousOutputIndex(0);
                        newTransactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
                        newTransactionInput.setUnlockingScript(Script.EMPTY_SCRIPT);

                        newTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
                        newTransactionOutput.setIndex(0);
                        newTransactionOutput.setLockingScript(ScriptBuilder.payToAddress(Address.fromPrivateKey(PrivateKey.parseFromHexString("D4BF010D3EC25F913CFF91CA34FD4C04A38908E0478B31F669B647EFCD2482A5"))));

                        newTransaction.setVersion(1);
                        newTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
                        newTransaction.setHasWitnessData(false);
                        newTransaction.addTransactionInput(newTransactionInput);
                        newTransaction.addTransactionOutput(newTransactionOutput);
                    }

                    final TransactionSigner transactionSigner = new TransactionSigner();
                    final SignatureContext signatureContext = new SignatureContext(newTransaction, ScriptSignature.HashType.SIGNATURE_HASH_ALL); {
                        signatureContext.setShouldSignInput(0, true, coinbaseTransactionOutput);
                        signatureContext.setShouldSignOutput(0, true);
                    }
                    final Transaction newSignedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);

                    prototypeBlock.setVersion(1);
                    prototypeBlock.setPreviousBlockHash(previousBlock.getHash());
                    prototypeBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                    prototypeBlock.setNonce(0L);
                    prototypeBlock.setDifficulty(new ImmutableDifficulty(ByteUtil.integerToBytes(Difficulty.BASE_DIFFICULTY_SIGNIFICAND), Difficulty.BASE_DIFFICULTY_EXPONENT));
                    prototypeBlock.addTransaction(coinbaseTransaction);
                    prototypeBlock.addTransaction(newSignedTransaction);
                }

                final Miner miner = new Miner(4, 0);
                final Block block = miner.mineBlock(prototypeBlock);

                final BlockDeflater blockDeflater = new BlockDeflater();
                Logger.log(block.getHash());
                Logger.log(BitcoinUtil.toHexString(blockDeflater.toBytes(block)));
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

                        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
                        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

                        blockDatabaseManager.storeBlock(block);
                        blockChainDatabaseManager.updateBlockChainsForNewBlock(block);

                        final BlockValidator blockValidator = new BlockValidator(databaseConnection);
                        final Boolean blockIsValid = blockValidator.validateBlock(null, block);

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