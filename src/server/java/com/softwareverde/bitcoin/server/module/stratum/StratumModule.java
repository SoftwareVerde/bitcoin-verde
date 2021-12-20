package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumDataHandler;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.AuthenticateApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.CreateAccountApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.PasswordApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.PayoutAddressApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.UnauthenticateApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.ValidateAuthenticationApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.admin.AuthenticateAdminApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.admin.UnauthenticateAdminApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.admin.ValidateAdminAuthenticationApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.admin.WorkerDifficultyApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker.CreateWorkerApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker.DeleteWorkerApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker.GetWorkersApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool.PoolHashRateApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool.PoolPrototypeBlockApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool.PoolWorkerApi;
import com.softwareverde.bitcoin.server.module.stratum.callback.BlockFoundCallback;
import com.softwareverde.bitcoin.server.module.stratum.callback.WorkerShareCallback;
import com.softwareverde.bitcoin.server.module.stratum.database.WorkerDatabaseManager;
import com.softwareverde.bitcoin.server.module.stratum.key.ServerEncryptionKey;
import com.softwareverde.bitcoin.server.module.stratum.rpc.StratumRpcServer;
import com.softwareverde.bitcoin.server.properties.DatabasePropertiesStore;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.servlet.DirectoryServlet;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;

public class StratumModule {
    protected static final String COINBASE_PRIVATE_KEY_KEY = "private_key_cipher";

    protected final SystemTime _systemTime = new SystemTime();

    protected final Environment _environment;
    protected final StratumProperties _stratumProperties;
    protected final DatabasePropertiesStore _databasePropertiesStore;
    protected final StratumServer _stratumServer;
    protected final StratumRpcServer _stratumRpcServer;
    protected final HttpServer _apiServer = new HttpServer();

    protected final CachedThreadPool _stratumThreadPool = new CachedThreadPool(256, 60000L);
    protected final CachedThreadPool _rpcThreadPool = new CachedThreadPool(256, 60000L);

    protected <T extends Servlet> void _assignEndpoint(final String path, final T servlet) {
        final Endpoint endpoint = new Endpoint(servlet);
        endpoint.setStrictPathEnabled(true);
        endpoint.setPath(path);

        _apiServer.addEndpoint(endpoint);
    }

    public StratumModule(final StratumProperties stratumProperties, final Environment environment) {
        this(stratumProperties, environment, false);
    }

    public StratumModule(final StratumProperties stratumProperties, final Environment environment, final Boolean useBitcoinCoreStratumServer) {
        _stratumProperties = stratumProperties;
        _environment = environment;

        final Database database = _environment.getDatabase();
        final DatabaseConnectionFactory databaseConnectionFactory = database.newConnectionFactory();

        _databasePropertiesStore = new DatabasePropertiesStore(databaseConnectionFactory);
        if (useBitcoinCoreStratumServer) {
            _stratumServer = new BitcoinCoreStratumServer(_stratumProperties, _databasePropertiesStore, _stratumThreadPool);
        }
        else {
            _stratumServer = new BitcoinVerdeStratumServer(_stratumProperties, _databasePropertiesStore, _stratumThreadPool);
        }

        _stratumServer.setWorkerShareCallback(new WorkerShareCallback() {
            @Override
            public void onNewWorkerShare(final String workerUsername, final Long shareDifficulty) {
                try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    final WorkerDatabaseManager workerDatabaseManager = new WorkerDatabaseManager(databaseConnection);
                    final WorkerId workerId = workerDatabaseManager.getWorkerId(workerUsername);
                    if (workerId == null) {
                        Logger.debug("Unknown worker: " + workerUsername);
                    }
                    else {
                        workerDatabaseManager.addWorkerShare(workerId, shareDifficulty);
                        Logger.debug("Added worker share: " + workerUsername + " " + shareDifficulty);
                    }
                }
                catch (final DatabaseException databaseException) {
                    Logger.warn("Unable to add worker share: " + workerUsername + " " + shareDifficulty, databaseException);
                }
            }
        });

        _stratumServer.setBlockFoundCallback(new BlockFoundCallback() {
            @Override
            public void run(final Block block, final String workerName) {
                final Sha256Hash blockHash = block.getHash();
                try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    final WorkerDatabaseManager workerDatabaseManager = new WorkerDatabaseManager(databaseConnection);
                    final WorkerId workerId = workerDatabaseManager.getWorkerId(workerName);

                    workerDatabaseManager.recordFoundBlock(blockHash, workerId);

                    // Store block...
                    final BlockDeflater blockDeflater = new BlockDeflater();
                    final ByteArray blockBytes = blockDeflater.toBytes(block);
                    final String dataDirectory = _stratumProperties.getDataDirectory();
                    final File blockFile = new File(dataDirectory, blockHash.toString());
                    IoUtil.putFileContents(blockFile, blockBytes);
                }
                catch (final DatabaseException databaseException) {
                    Logger.warn("Unable to record found block: " + blockHash, databaseException);
                }
            }
        });

        final String tlsKeyFile = stratumProperties.getTlsKeyFile();
        final String tlsCertificateFile = stratumProperties.getTlsCertificateFile();
        if ( (tlsKeyFile != null) && (tlsCertificateFile != null) ) {
            _apiServer.setTlsPort(stratumProperties.getTlsPort());
            _apiServer.setCertificate(stratumProperties.getTlsCertificateFile(), stratumProperties.getTlsKeyFile());
            _apiServer.enableEncryption(true);
            _apiServer.redirectToTls(false);
        }

        _apiServer.setPort(stratumProperties.getHttpPort());

        final StratumDataHandler stratumDataHandler = new StratumDataHandler() {
            @Override
            public Block getPrototypeBlock() {
                return _stratumServer.getPrototypeBlock();
            }

            @Override
            public Long getPrototypeBlockHeight() {
                return _stratumServer.getBlockHeight();
            }

            @Override
            public Long getHashesPerSecond() {
                final Long hashesPerSecondMultiplier = (1L << 32);
                final Long shareDifficulty = _stratumServer.getShareDifficulty();
                final Long startTimeInSeconds = _stratumServer.getCurrentBlockStartTimeInSeconds();
                final Long shareCount = _stratumServer.getShareCount();

                final Long now = _systemTime.getCurrentTimeInSeconds();
                final long duration = (now - startTimeInSeconds);

                return (long) (shareDifficulty * hashesPerSecondMultiplier * (shareCount / (double) duration));
            }
        };

        { // Api Endpoints
            final WorkerDifficultyApi workerDifficultyApi;
            {
                workerDifficultyApi = new WorkerDifficultyApi(stratumProperties, _databasePropertiesStore);
                workerDifficultyApi.setShareDifficultyUpdatedCallback(new Runnable() {
                    @Override
                    public void run() {
                        final Long workerDifficulty = WorkerDifficultyApi.getShareDifficulty(_databasePropertiesStore);
                        _stratumServer.setShareDifficulty(workerDifficulty);
                    }
                });
            }

            _assignEndpoint("/api/v1/admin/authenticate", new AuthenticateAdminApi(stratumProperties, _databasePropertiesStore));
            _assignEndpoint("/api/v1/admin/validate", new ValidateAdminAuthenticationApi(stratumProperties));
            _assignEndpoint("/api/v1/admin/unauthenticate", new UnauthenticateAdminApi(stratumProperties));
            _assignEndpoint("/api/v1/admin/worker/difficulty", workerDifficultyApi);

            _assignEndpoint("/api/v1/worker", new PoolWorkerApi(stratumProperties, stratumDataHandler));
            _assignEndpoint("/api/v1/pool/prototype-block", new PoolPrototypeBlockApi(stratumProperties, stratumDataHandler));
            _assignEndpoint("/api/v1/pool/hash-rate", new PoolHashRateApi(stratumProperties, stratumDataHandler));
            _assignEndpoint("/api/v1/account/create", new CreateAccountApi(stratumProperties, databaseConnectionFactory));
            _assignEndpoint("/api/v1/account/authenticate", new AuthenticateApi(stratumProperties, databaseConnectionFactory));
            _assignEndpoint("/api/v1/account/validate", new ValidateAuthenticationApi(stratumProperties));
            _assignEndpoint("/api/v1/account/unauthenticate", new UnauthenticateApi(stratumProperties, databaseConnectionFactory));
            _assignEndpoint("/api/v1/account/address", new PayoutAddressApi(stratumProperties, databaseConnectionFactory));
            _assignEndpoint("/api/v1/account/password", new PasswordApi(stratumProperties, databaseConnectionFactory));
            _assignEndpoint("/api/v1/account/workers/create", new CreateWorkerApi(stratumProperties, databaseConnectionFactory));
            _assignEndpoint("/api/v1/account/workers/delete", new DeleteWorkerApi(stratumProperties, databaseConnectionFactory));
            _assignEndpoint("/api/v1/account/workers", new GetWorkersApi(stratumProperties, databaseConnectionFactory));
        }

        { // Static Content
            final File servedDirectory = new File(stratumProperties.getRootDirectory() + "/");
            final DirectoryServlet indexServlet = new DirectoryServlet(servedDirectory);
            indexServlet.setShouldServeDirectories(true);
            indexServlet.setIndexFile("index.html");

            final Endpoint endpoint = new Endpoint(indexServlet);
            endpoint.setPath("/");
            endpoint.setStrictPathEnabled(false);
            _apiServer.addEndpoint(endpoint);
        }

        _stratumRpcServer = new StratumRpcServer(stratumProperties, stratumDataHandler, _rpcThreadPool);
    }

    public void loop() {
        final ServerEncryptionKey serverEncryptionKey;
        {
            final String dataDirectory = _stratumProperties.getDataDirectory();
            final File dataDirectoryFile = new File(dataDirectory);
            if (! dataDirectoryFile.isDirectory()) {
                dataDirectoryFile.mkdirs();
            }

            serverEncryptionKey = ServerEncryptionKey.load(dataDirectoryFile);
        }

        _rpcThreadPool.start();
        _stratumThreadPool.start();
        _databasePropertiesStore.start();

        { // Ensure the StratumServer's PropertiesStore has the correct payout address; if a payout address does not exist, then create an encrypted one.
            final Address coinbaseAddress;
            {
                final AddressInflater addressInflater = new AddressInflater();
                final String privateKeyCipher = _databasePropertiesStore.getString(StratumModule.COINBASE_PRIVATE_KEY_KEY);
                if (Util.isBlank(privateKeyCipher)) {
                    final PrivateKey privateKey = PrivateKey.createNewKey();
                    final String encryptedPrivateKey;
                    {
                        final ByteArray encryptedPrivateKeyBytes = serverEncryptionKey.encrypt(privateKey);
                        encryptedPrivateKey = encryptedPrivateKeyBytes.toString();
                    }
                    _databasePropertiesStore.set(StratumModule.COINBASE_PRIVATE_KEY_KEY, encryptedPrivateKey);

                    coinbaseAddress = addressInflater.fromPrivateKey(privateKey, true);
                }
                else {
                    final ByteArray privateKeyCipherBytes = ByteArray.fromHexString(privateKeyCipher);
                    final ByteArray privateKeyBytes = serverEncryptionKey.decrypt(privateKeyCipherBytes);
                    final PrivateKey privateKey = PrivateKey.fromBytes(privateKeyBytes);

                    coinbaseAddress = addressInflater.fromPrivateKey(privateKey, true);
                }
            }

            final String coinbaseAddressString = coinbaseAddress.toBase32CheckEncoded();
            _databasePropertiesStore.set(BitcoinCoreStratumServer.COINBASE_ADDRESS_KEY, coinbaseAddressString);
        }

        { // Ensure the StratumServer has a share difficulty set...
            if (! WorkerDifficultyApi.isShareDifficultySet(_databasePropertiesStore)) {
                WorkerDifficultyApi.setShareDifficulty(2048L, _databasePropertiesStore);
            }
        }

        { // Ensure the StratumServer has an admin password...
            if (! AuthenticateAdminApi.isAdminPasswordSet(_databasePropertiesStore)) {
                AuthenticateAdminApi.setDefaultAdminPassword(_databasePropertiesStore);
            }
        }

        _stratumRpcServer.start();
        _stratumServer.start();
        _apiServer.start();

        while (true) {
            try { Thread.sleep(60000L); } catch (final Exception exception) { break; }
        }

        _apiServer.stop();
        _stratumServer.stop();
        _stratumRpcServer.stop();

        _databasePropertiesStore.stop();
        _stratumThreadPool.stop();
        _rpcThreadPool.stop();
    }
}