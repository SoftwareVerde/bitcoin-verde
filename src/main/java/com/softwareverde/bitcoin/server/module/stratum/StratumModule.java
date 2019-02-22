package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.PoolApi;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumDataHandler;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.WorkerApi;
import com.softwareverde.bitcoin.server.module.stratum.rpc.StratumRpcServer;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.httpserver.DirectoryServlet;
import com.softwareverde.httpserver.HttpServer;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.Endpoint;
import com.softwareverde.servlet.Servlet;

import java.io.File;

public class StratumModule {
    public static void execute(final String configurationFileName) {
        final StratumModule stratumModule = new StratumModule(configurationFileName);
        stratumModule.loop();
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected final Configuration _configuration;
    protected final StratumServer _stratumServer;
    protected final StratumRpcServer _stratumRpcServer;
    protected final HttpServer _apiServer = new HttpServer();

    protected final MainThreadPool _apiServerThreadPool = new MainThreadPool(256, 60000L);
    protected final MainThreadPool _stratumThreadPool = new MainThreadPool(256, 60000L);
    protected final MainThreadPool _rpcThreadPool = new MainThreadPool(256, 60000L);

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected <T extends Servlet> void _assignEndpoint(final String path, final T servlet) {
        final Endpoint endpoint = new Endpoint(servlet);
        endpoint.setStrictPathEnabled(true);
        endpoint.setPath(path);

        _apiServer.addEndpoint(endpoint);
    }

    public StratumModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);
        _stratumServer = new StratumServer(_configuration.getStratumProperties(), _stratumThreadPool);

        final Configuration.StratumProperties stratumProperties = _configuration.getStratumProperties();
        final Configuration.DatabaseProperties databaseProperties = stratumProperties.getDatabaseProperties();

        final MysqlDatabase database = Database.newInstance(Database.STRATUM, databaseProperties);
        if (database == null) {
            Logger.log("Error initializing database.");
            BitcoinUtil.exitFailure();
        }
        Logger.log("[Database Online]");

        final String tlsKeyFile = stratumProperties.getTlsKeyFile();
        final String tlsCertificateFile = stratumProperties.getTlsCertificateFile();
        if ( (tlsKeyFile != null) && (tlsCertificateFile != null) ) {
            _apiServer.setTlsPort(stratumProperties.getTlsPort());
            _apiServer.setCertificate(stratumProperties.getTlsCertificateFile(), stratumProperties.getTlsKeyFile());
            _apiServer.enableEncryption(true);
            _apiServer.redirectToTls(false); // Disabled due to a bug in HttpServer...
        }

        _apiServer.setPort(stratumProperties.getHttpPort());

        final StratumDataHandler stratumDataHandler = new StratumDataHandler() {
            @Override
            public Block getPrototypeBlock() {
                return _stratumServer.getPrototypeBlock();
            }
        };

        { // Api Endpoints
            _assignEndpoint("/api/v1/worker", new WorkerApi(stratumProperties, stratumDataHandler, _apiServerThreadPool));
            _assignEndpoint("/api/v1/pool", new PoolApi(stratumProperties, stratumDataHandler, _apiServerThreadPool));
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
        _stratumRpcServer.start();
        _stratumServer.start();
        _apiServer.start();

        while (true) {
            try { Thread.sleep(60000L); } catch (final Exception exception) { break; }
        }

        _apiServer.stop();
        _stratumServer.stop();
        _stratumRpcServer.stop();
    }
}