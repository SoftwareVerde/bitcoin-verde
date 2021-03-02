package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.block.Block;
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
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker.CreateWorkerApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker.DeleteWorkerApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.account.worker.GetWorkersApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool.PoolHashRateApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool.PoolPrototypeBlockApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.pool.PoolWorkerApi;
import com.softwareverde.bitcoin.server.module.stratum.database.AccountDatabaseManager;
import com.softwareverde.bitcoin.server.module.stratum.rpc.StratumRpcServer;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.servlet.DirectoryServlet;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;

public class StratumModule {
    protected final SystemTime _systemTime = new SystemTime();

    protected final Environment _environment;
    protected final StratumProperties _stratumProperties;
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
        this(stratumProperties, environment, true);
    }

    public StratumModule(final StratumProperties stratumProperties, final Environment environment, final Boolean useBitcoinCoreStratumServer) {
        _stratumProperties = stratumProperties;
        _environment = environment;

        final Database database = _environment.getDatabase();
        final DatabaseConnectionFactory databaseConnectionFactory = database.newConnectionFactory();

        if (useBitcoinCoreStratumServer) {
            _stratumServer = new BitcoinCoreStratumServer(_stratumProperties, _stratumThreadPool);
        }
        else {
            _stratumServer = new BitcoinVerdeStratumServer(_stratumProperties, _stratumThreadPool);
        }

        _stratumServer.setWorkerShareCallback(new WorkerShareCallback() {
            @Override
            public void onNewWorkerShare(final String workerUsername, final Integer shareDifficulty) {
                try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    final AccountDatabaseManager accountDatabaseManager = new AccountDatabaseManager(databaseConnection);
                    final WorkerId workerId = accountDatabaseManager.getWorkerId(workerUsername);
                    if (workerId == null) {
                        Logger.debug("Unknown worker: " + workerUsername);
                    }
                    else {
                        accountDatabaseManager.addWorkerShare(workerId, shareDifficulty);
                        Logger.debug("Added worker share: " + workerUsername + " " + shareDifficulty);
                    }
                }
                catch (final DatabaseException databaseException) {
                    Logger.warn("Unable to add worker share: " + workerUsername + " " + shareDifficulty, databaseException);
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
                final Integer shareDifficulty = _stratumServer.getShareDifficulty();
                final Long startTimeInSeconds = _stratumServer.getCurrentBlockStartTimeInSeconds();
                final Long shareCount = _stratumServer.getShareCount();

                final Long now = _systemTime.getCurrentTimeInSeconds();
                final Long duration = (now - startTimeInSeconds);

                return (long) (shareDifficulty * hashesPerSecondMultiplier * (shareCount / duration.doubleValue()));
            }
        };

        { // Api Endpoints
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
        _rpcThreadPool.start();
        _stratumThreadPool.start();

        _stratumRpcServer.start();
        _stratumServer.start();
        _apiServer.start();

        while (true) {
            try { Thread.sleep(60000L); } catch (final Exception exception) { break; }
        }

        _apiServer.stop();
        _stratumServer.stop();
        _stratumRpcServer.stop();

        _stratumThreadPool.stop();
        _rpcThreadPool.stop();
    }
}