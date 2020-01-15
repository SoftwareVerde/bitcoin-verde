package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.block.validator.BlockValidatorFactory;
import com.softwareverde.bitcoin.block.validator.BlockValidatorFactoryCore;
import com.softwareverde.bitcoin.miner.GpuSha256;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.configuration.*;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCacheCore;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.pool.hikari.HikariDatabaseConnectionPool;
import com.softwareverde.bitcoin.server.module.*;
import com.softwareverde.bitcoin.server.module.explorer.ExplorerModule;
import com.softwareverde.bitcoin.server.module.node.NodeModule;
import com.softwareverde.bitcoin.server.module.proxy.ProxyModule;
import com.softwareverde.bitcoin.server.module.stratum.StratumModule;
import com.softwareverde.bitcoin.server.module.wallet.WalletModule;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.jocl.JoclGpuSha256;
import com.softwareverde.logging.BitcoinNodeLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.log.SystemLog;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;

import java.io.File;

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
        Logger.setLog(BitcoinNodeLog.getInstance());
        Logger.setLogLevel(LogLevel.ON);
        Logger.setLogLevel("com.softwareverde.util", LogLevel.ERROR);
        Logger.setLogLevel("com.softwareverde.network", LogLevel.INFO);
        Logger.setLogLevel("com.softwareverde.async.lock", LogLevel.WARN);

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
        _printError("\tDescription: Connects to a remote node and begins downloading and validating the block chain.");
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

        _printError("\tModule: REPAIR");
        _printError("\tArguments: <Configuration File> <Block Hash> [<Block Hash> [...]]");
        _printError("\tDescription: Downloads the requested blocks and repairs the database with the blocks received from the configured trusted peer.");
        _printError("\tArgument Description: <Configuration File>");
        _printError("\t\tThe path and filename of the configuration file for running the node.  Ex: conf/server.conf");
        _printError("\tArgument Description: <Block Hash>");
        _printError("\t\tThe block that should be repaired. Multiple blocks may be specified, each separated by a space.");
        _printError("\t\tEx: 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F");
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
        _printError("\tArguments: <Key File> <Message>");
        _printError("\tDescription: Signs the provided message with the private key within the provided file.  The signed pre-image hash is a double sha256 hash of the message.");
        _printError("\tArgument Description: <Key File>");
        _printError("\t\tThe path and filename of the file containing the private key to sign in either ASCII-Hex format or Seed Phrase format.");
        _printError("\tArgument Description: <Message>");
        _printError("\t\tThe message to be signed.");
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

                // Logger.LOG = BitcoinNodeLog.getBufferedInstance(); // Use a BufferedInstance for the NodeModule for performance...
                Logger.setLog(BitcoinNodeLog.getInstance()); // Use a BufferedInstance for the NodeModule for performance...

                final String configurationFilename = _arguments[1];
                final Configuration configuration = _loadConfigurationFile(configurationFilename);

                final BitcoinProperties bitcoinProperties = configuration.getBitcoinProperties();
                final DatabaseProperties databaseProperties = configuration.getBitcoinDatabaseProperties();

                final Container<NodeModule> nodeModuleContainer = new Container<NodeModule>();
                final Database database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.BITCOIN, databaseProperties, new Runnable() {
                    @Override
                    public void run() {
                        final NodeModule nodeModule = nodeModuleContainer.value;
                        if (nodeModule != null) {
                            nodeModule.shutdown();
                        }
                    }
                });
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                    throw new RuntimeException("");
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new HikariDatabaseConnectionPool(databaseProperties);
                final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCacheCore();

                final Environment environment = new Environment(database, databaseConnectionPool, masterDatabaseManagerCache);

                nodeModuleContainer.value = new NodeModule(bitcoinProperties, environment);
                nodeModuleContainer.value.loop();
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
                final DatabaseProperties databaseProperties = configuration.getBitcoinDatabaseProperties();

                final Database database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.BITCOIN, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new HikariDatabaseConnectionPool(databaseProperties);
                final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCacheCore();
                final Environment environment = new Environment(database, databaseConnectionPool, masterDatabaseManagerCache);
                final BlockValidatorFactory blockValidatorFactory = new BlockValidatorFactoryCore();

                final ChainValidationModule chainValidationModule = new ChainValidationModule(bitcoinProperties, environment, startingBlockHash, blockValidatorFactory);
                chainValidationModule.run();
                Logger.flush();
            } break;

            case "REPAIR": {
                if (_arguments.length < 3) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String configurationFilename = _arguments[1];
                final String[] blockHashes = new String[_arguments.length - 2];
                for (int i = 0; i < blockHashes.length; ++i) {
                    blockHashes[i] = _arguments[2 + i];
                }

                final Configuration configuration = _loadConfigurationFile(configurationFilename);

                final BitcoinProperties bitcoinProperties = configuration.getBitcoinProperties();
                final DatabaseProperties databaseProperties = configuration.getBitcoinDatabaseProperties();

                final Database database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.BITCOIN, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new HikariDatabaseConnectionPool(databaseProperties);
                final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCacheCore();
                final Environment environment = new Environment(database, databaseConnectionPool, masterDatabaseManagerCache);

                final RepairModule repairModule = new RepairModule(bitcoinProperties, environment, blockHashes);
                repairModule.run();
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
                final DatabaseProperties databaseProperties = configuration.getStratumDatabaseProperties();
                final Database database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.STRATUM, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new HikariDatabaseConnectionPool(databaseProperties);
                final Environment environment = new Environment(database, databaseConnectionPool, null);

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
                final DatabaseProperties databaseProperties = configuration.getBitcoinDatabaseProperties();

                final Database database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.BITCOIN, databaseProperties);
                if (database == null) {
                    Logger.error("Error initializing database.");
                    BitcoinUtil.exitFailure();
                }
                Logger.info("[Database Online]");

                final DatabaseConnectionPool databaseConnectionPool = new HikariDatabaseConnectionPool(databaseProperties);
                final Environment environment = new Environment(database, databaseConnectionPool, null);
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

                if (_arguments.length != 3) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String keyFileName = _arguments[1];
                final String message = _arguments[2];

                SignatureModule.execute(keyFileName, message);
                Logger.flush();
            } break;

            case "MINER": {
                if (_arguments.length != 5) {
                    _printUsage();
                    BitcoinUtil.exitFailure();
                    break;
                }

                final String previousBlockHashString = _arguments[1];
                final String base58CheckAddress = _arguments[2];
                final Integer cpuThreadCount = Util.parseInt(_arguments[3]);
                final Integer gpuThreadCount = Util.parseInt(_arguments[4]);
                final GpuSha256 gpuSha256 = JoclGpuSha256.getInstance();
                final MinerModule minerModule = new MinerModule(previousBlockHashString, base58CheckAddress, cpuThreadCount, gpuThreadCount, gpuSha256);
                minerModule.run();
                Logger.flush();
            } break;

            default: {
                _printUsage();
                BitcoinUtil.exitFailure();
            }
        }
    }
}