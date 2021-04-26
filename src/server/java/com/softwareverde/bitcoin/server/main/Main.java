package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.BitcoinVerdeDatabaseProperties;
import com.softwareverde.bitcoin.server.configuration.Configuration;
import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.configuration.NodeProperties;
import com.softwareverde.bitcoin.server.configuration.ProxyProperties;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.configuration.WalletProperties;
import com.softwareverde.bitcoin.server.database.pool.ApacheCommonsDatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.module.AddressModule;
import com.softwareverde.bitcoin.server.module.ChainValidationModule;
import com.softwareverde.bitcoin.server.module.ConfigurationModule;
import com.softwareverde.bitcoin.server.module.DatabaseModule;
import com.softwareverde.bitcoin.server.module.MinerModule;
import com.softwareverde.bitcoin.server.module.SignatureModule;
import com.softwareverde.bitcoin.server.module.explorer.ExplorerModule;
import com.softwareverde.bitcoin.server.module.node.NodeModule;
import com.softwareverde.bitcoin.server.module.proxy.ProxyModule;
import com.softwareverde.bitcoin.server.module.spv.SpvModule;
import com.softwareverde.bitcoin.server.module.stratum.StratumModule;
import com.softwareverde.bitcoin.server.module.wallet.WalletModule;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.Wallet;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.Log;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.filelog.AnnotatedFileLog;
import com.softwareverde.logging.log.SystemLog;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.IOException;

public class Main {

    protected static Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    public static void main(final String[] commandLineArguments) {
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.ON);
        Logger.setLogLevel("com.softwareverde.util", LogLevel.ERROR);
        Logger.setLogLevel("com.softwareverde.network", LogLevel.INFO);
        Logger.setLogLevel("com.softwareverde.async.lock", LogLevel.WARN);
        Logger.setLogLevel("org.apache.commons", LogLevel.WARN);
        Logger.setLogLevel("com.zaxxer.hikari", LogLevel.WARN);
        Logger.setLogLevel("ch.vorburger.exec", LogLevel.WARN);
        Logger.setLogLevel("ch.vorburger.mariadb4j", LogLevel.WARN);

        final Main application = new Main(commandLineArguments);
        application.run();
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected void _printUsage() {
        final String commandString = "java -jar " + System.getProperty("java.class.path");

        _printError("Usage: " + commandString + " <Module> <Arguments>");
        _printError("");

        _printError("\tModule: NODE");
        _printError("\tArguments: <Configuration File>");
        _printError("\tDescription: Connects to the BCH network and begins downloading and validating the block chain.");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the node.  Ex: conf/server.conf");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: EXPLORER");
        _printError("\tArguments: <Configuration File>");
        _printError("\tDescription: Starts a web server that provides an interface to explore the block chain.");
        _printError("\t\tThe explorer does not synchronize with the network, therefore NODE should be executed beforehand or in parallel.");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the node.  Ex: conf/server.conf");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: SPV");
        _printError("\tArguments: <Configuration File>");
        _printError("\tDescription: Connects to the BCH network as a simple payment verification node.");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the node.  Ex: conf/server.conf");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: WALLET");
        _printError("\tArguments: <Configuration File>");
        _printError("\tDescription: Starts a web server that provides an interface to explore the block chain.");
        _printError("\t\tThe explorer does not synchronize with the network, therefore NODE should be executed beforehand or in parallel.");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the node.  Ex: conf/server.conf");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: VALIDATE");
        _printError("\tArguments: <Configuration File> [<Starting Block Hash>]");
        _printError("\tDescription: Iterates through the entire block chain and identifies any invalid/corrupted blocks.");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the node.  Ex: conf/server.conf");
        _printError("\tArgument Description: <Starting Block Hash>");
        _printError("\t\tThe first block that should be validated; used to skip validation of early sections of the chain, or to resume.");
        _printError("\t\tEx: 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: STRATUM");
        _printError("\tArguments: <Configuration File>");
        _printError("\tDescription: Starts a Stratum server for pooled mining.");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the stratum server.  Ex: conf/server.conf");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: DATABASE");
        _printError("\tArguments: <Configuration File>");
        _printError("\tDescription: Starts the database so that it may be explored via MySQL.");
        _printError("\t\tTo connect to the database, use the settings provided within your configuration file.  Ex: mysql -u bitcoin -h 127.0.0.1 -P8336 -p81b797117e8e0233ea8fd1d46923df54 bitcoin");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the node.  Ex: conf/server.conf");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: ADDRESS");
        _printError("\tArguments:");
        _printError("\tDescription: Generates a private key and its associated public key and Base58Check Bitcoin address.");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: SIGNATURE");
        _printError("\tArguments: SIGN <Key File> <Message> <Use Compressed Address>");
        _printError("\tArguments: VERIFY <Address> <Signature> <Message>");
        _printError("\tDescription: Signs a message with a private key within a file, or verifies a signature against the provided address.");
        _printError("\tArgument Description: <Key File>");
        _printError("\t\tThe path and filename of the file containing the private key to sign in either ASCII-Hex format or Seed Phrase format.");
        _printError("\tArgument Description: <Message>");
        _printError("\t\tThe message to be signed when executing in SIGN mode, or the signed message when executing in VERIFY mode.");
        _printError("\tArgument Description: <Use Compressed Address>");
        _printError("\t\tWhether or not the address signing the message should be the compressed or uncompressed address.");
        _printError("\tArgument Description: <Address>");
        _printError("\t\tThe address used to sign the message in Base-58 format.");
        _printError("\tArgument Description: <Signature>");
        _printError("\t\tThe signature to verify against the address and message in Base-64 format.");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: MINER");
        _printError("\tArguments: <Previous Block Hash> <Bitcoin Address> <CPU Thread Count> <GPU Thread Count>");
        _printError("\tDescription: Creates a block based off the provided previous-block-hash, with a single coinbase transaction to the address provided.");
        _printError("\t\tThe block created will be for initial Bitcoin difficulty.  This mode is intended to be used to generate test-blocks.");
        _printError("\t\tThe block created is not relayed over the network.");
        _printError("\tArgument Description: <Previous Block Hash>");
        _printError("\t\tThe Hex-String-Encoded Block-Hash to use as the new block's previous block.  Ex: 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F");
        _printError("\tArgument Description: <Bitcoin Address>");
        _printError("\t\tThe Base58Check encoded Bitcoin Address to send the coinbase's newly generated coins.  Ex: 1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW");
        _printError("\tArgument Description: <CPU Thread Count>");
        _printError("\t\tThe number of CPU threads to be spawned while attempting to find a suitable block hash.  Ex: 4");
        _printError("\tArgument Description: <GPU Thread Count>");
        _printError("\t\tThe number of GPU threads to be spawned while attempting to find a suitable block hash.  Ex: 0");
        _printError("\t\tNOTE: on a Mac Pro, it is best to leave this as zero.");
        _printError("\t----------------");
        _printError("");

        _printError("\tModule: CONFIGURATION");
        _printError("\tArguments:");
        _printError("\tDescription: Generates a new configuration file based on input from the user.");
        _printError("\t----------------");
        _printError("");
    }

    protected final String[] _arguments;

    public Main(final String[] arguments) {
        _arguments = arguments;

        if (arguments.length < 1) {
            _printUsage();
            BitcoinUtil.exitFailure();
        }
    }

    public void run() {
        final String module = _arguments[0].toUpperCase();
        switch (module) {

            case "NODE": {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final Configuration configuration = _loadConfigurationFile(configurationFilename);

                final BitcoinProperties bitcoinProperties = configuration.getBitcoinProperties();
                final BitcoinVerdeDatabaseProperties databaseProperties = configuration.getBitcoinDatabaseProperties();

                if (bitcoinProperties.isTestNet()) {
                    BitcoinConstants.configureForNetwork(NetworkType.TEST_NET);
                }

                { // Set Log Level...
                    try {
                        final String logDirectory = bitcoinProperties.getLogDirectory();
                        final Log log = AnnotatedFileLog.newInstance(logDirectory, "node");
                        Logger.setLog(log);

                        final Runtime runtime = Runtime.getRuntime();
                        runtime.addShutdownHook(new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Logger.close();
                            }
                        }));
                    }
                    catch (final IOException exception) {
                        Logger.warn("Unable to initialize file logger.", exception);
                        BitcoinUtil.exitFailure();
                    }

                    final LogLevel logLevel = bitcoinProperties.getLogLevel();
                    if (logLevel != null) {
                        Logger.setLogLevel(logLevel);
                    }
                }

                final Container<NodeModule> nodeModuleContainer = new Container<NodeModule>();
                final BitcoinVerdeDatabase database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.BITCOIN, bitcoinProperties, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                    throw new RuntimeException("");
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionFactory = new ApacheCommonsDatabaseConnectionPool(databaseProperties, database.getMaxDatabaseConnectionCount());
                // final DatabaseConnectionPool databaseConnectionFactory = new SimpleDatabaseConnectionPool(database, 8);

                final Environment environment = new Environment(database, databaseConnectionFactory);

                nodeModuleContainer.value = new NodeModule(bitcoinProperties, environment);
                nodeModuleContainer.value.loop();
                Logger.flush();
            } break;

            case "SPV": {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final Configuration configuration = _loadConfigurationFile(configurationFilename);

                final BitcoinProperties bitcoinProperties = configuration.getBitcoinProperties();
                final BitcoinVerdeDatabaseProperties databaseProperties = configuration.getSpvDatabaseProperties();

                if (bitcoinProperties.isTestNet()) {
                    BitcoinConstants.configureForNetwork(NetworkType.TEST_NET);
                }

                { // Set Log Level...
                    Logger.setLog(LineNumberAnnotatedLog.getInstance());
                    final LogLevel logLevel = bitcoinProperties.getLogLevel();
                    if (logLevel != null) {
                        Logger.setLogLevel(logLevel);
                    }
                }

                final Container<SpvModule> spvModuleContainer = new Container<>();
                final BitcoinVerdeDatabase database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.SPV, bitcoinProperties, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                    throw new RuntimeException("");
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionFactory = new ApacheCommonsDatabaseConnectionPool(databaseProperties, database.getMaxDatabaseConnectionCount());
                // final DatabaseConnectionPool databaseConnectionFactory = new SimpleDatabaseConnectionPool(database, databaseConnectionCacheCount);

                final Environment environment = new Environment(database, databaseConnectionFactory);

                final List<NodeProperties> seedNodes = bitcoinProperties.getSeedNodeProperties();
                final Boolean isTestNet = bitcoinProperties.isTestNet();

                final Wallet wallet = new Wallet();

                final Thread mainThread = Thread.currentThread();
                final Runtime runtime = Runtime.getRuntime();
                runtime.addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mainThread.interrupt();
                            mainThread.join();
                        }
                        catch (final Exception exception) { }
                    }
                }));

                spvModuleContainer.value = new SpvModule(environment, seedNodes, 8, wallet, isTestNet);
                spvModuleContainer.value.initialize();
                spvModuleContainer.value.loop();
                Logger.flush();
            } break;

            case "EXPLORER": {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final Configuration configuration = _loadConfigurationFile(configurationFilename);
                final ExplorerProperties explorerProperties = configuration.getExplorerProperties();
                final ExplorerModule explorerModule = new ExplorerModule(explorerProperties);
                explorerModule.start();
                explorerModule.loop();
                explorerModule.stop();
            } break;

            case "WALLET": {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final Configuration configuration = _loadConfigurationFile(configurationFilename);
                final WalletProperties walletProperties = configuration.getWalletProperties();
                final WalletModule walletModule = new WalletModule(walletProperties);
                walletModule.start();
                walletModule.loop();
                walletModule.stop();
                Logger.flush();
            } break;

            case "VALIDATE": {
                if (_arguments.length < 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final String startingBlockHash = (_arguments.length > 2 ? _arguments[2] : "");

                final Configuration configuration = _loadConfigurationFile(configurationFilename);
                final BitcoinProperties bitcoinProperties = configuration.getBitcoinProperties();
                final BitcoinVerdeDatabaseProperties databaseProperties = configuration.getBitcoinDatabaseProperties();

                final BitcoinVerdeDatabase database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.BITCOIN, bitcoinProperties, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                    throw new RuntimeException("");
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new ApacheCommonsDatabaseConnectionPool(databaseProperties, database.getMaxDatabaseConnectionCount());
                // final DatabaseConnectionPool databaseConnectionPool = new SimpleDatabaseConnectionPool(database, databaseConnectionCacheCount);

                final Environment environment = new Environment(database, databaseConnectionPool);

                final ChainValidationModule chainValidationModule = new ChainValidationModule(bitcoinProperties, environment, startingBlockHash);
                chainValidationModule.run();
                Logger.flush();
            } break;

            case "STRATUM": {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFile = _arguments[1];

                final Configuration configuration = _loadConfigurationFile(configurationFile);
                final StratumProperties stratumProperties = configuration.getStratumProperties();
                final BitcoinVerdeDatabaseProperties databaseProperties = configuration.getStratumDatabaseProperties();
                final BitcoinVerdeDatabase database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.STRATUM, stratumProperties, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                    throw new RuntimeException("");
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new ApacheCommonsDatabaseConnectionPool(databaseProperties, database.getMaxDatabaseConnectionCount());
                final Environment environment = new Environment(database, databaseConnectionPool);

                final StratumModule stratumModule = new StratumModule(stratumProperties, environment);
                stratumModule.loop();
                Logger.flush();
            } break;

            case "PROXY": {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final Configuration configuration = _loadConfigurationFile(configurationFilename);
                final ProxyProperties proxyProperties = configuration.getProxyProperties();
                final StratumProperties stratumProperties = configuration.getStratumProperties();
                final ExplorerProperties explorerProperties = configuration.getExplorerProperties();
                final ProxyModule proxyModule = new ProxyModule(proxyProperties, stratumProperties, explorerProperties);
                proxyModule.loop();
                Logger.flush();
            } break;

            case "DATABASE": {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];

                final Configuration configuration = _loadConfigurationFile(configurationFilename);
                final BitcoinVerdeDatabaseProperties databaseProperties = configuration.getBitcoinDatabaseProperties();

                // prevent database upgrades
                final BitcoinVerdeDatabase.InitFile initFile = new BitcoinVerdeDatabase.InitFile(BitcoinVerdeDatabase.BITCOIN.sqlInitFile, null);

                final BitcoinVerdeDatabase database = BitcoinVerdeDatabase.newInstance(initFile, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                    throw new RuntimeException("");
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new ApacheCommonsDatabaseConnectionPool(databaseProperties, database.getMaxDatabaseConnectionCount());
                final Environment environment = new Environment(database, databaseConnectionPool);
                final DatabaseModule databaseModule = new DatabaseModule(environment);
                databaseModule.loop();
                Logger.flush();
            } break;

            case "ADDRESS": {
                final String desiredAddressPrefix = (_arguments.length > 1 ? _arguments[1] : "");
                final Boolean ignoreCase = (_arguments.length > 2 ? Util.parseBool(_arguments[2]) : false);
                AddressModule.execute(desiredAddressPrefix, ignoreCase);
                Logger.flush();
            } break;

            case "SIGNATURE": {
                Logger.setLog(SystemLog.getInstance());
                Logger.setLogLevel(LogLevel.WARN);

                if (_arguments.length != 5) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String action = _arguments[1];
                if (Util.areEqual("SIGN", action.toUpperCase())) {
                    final String keyFileName = _arguments[2];
                    final String message = _arguments[3];
                    final Boolean useCompressedAddress = Util.parseBool(_arguments[4]);

                    SignatureModule.executeSign(keyFileName, message, useCompressedAddress);
                    Logger.flush();
                    break;
                }
                else if (Util.areEqual("VERIFY", action.toUpperCase())) {
                    final String address = _arguments[2];
                    final String signature = _arguments[3];
                    final String message = _arguments[4];

                    SignatureModule.executeVerify(address, signature, message);
                    Logger.flush();
                    break;
                }

                _printUsage();
                BitcoinUtil.exitFailure();
                break;
            }

            case "MINER": {
                if (_arguments.length != 3) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final Integer cpuThreadCount = Util.parseInt(_arguments[1]);
                final String prototypeBlockBytes = _arguments[2];
                final MinerModule minerModule = new MinerModule(cpuThreadCount, prototypeBlockBytes);
                minerModule.run();
                Logger.flush();
            } break;


            case "CONFIGURATION" : {
                if (_arguments.length != 2) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final ConfigurationModule configurationModule = new ConfigurationModule(configurationFilename);
                configurationModule.run();
                Logger.flush();
            } break;

            default: {
                _printUsage();
                BitcoinUtil.exitFailure();
            }
        }
    }
}
