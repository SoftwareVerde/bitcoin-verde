package com.softwareverde.bitcoin.server.module.explorer;

import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.AnnouncementsApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.BlockchainApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.BlocksApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.DoubleSpendProofsApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.NodesApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.SearchApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.SlpApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.StatusApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.TransactionsApi;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.endpoint.WebSocketEndpoint;
import com.softwareverde.http.server.servlet.DirectoryServlet;
import com.softwareverde.http.server.servlet.Servlet;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class ExplorerModule {
    protected final HttpServer _apiServer = new HttpServer();
    protected final CachedThreadPool _threadPool = new CachedThreadPool(512, 1000L);
    protected final ExplorerProperties _explorerProperties;
    protected final AnnouncementsApi _announcementsApi;

    protected <T extends Servlet> void _assignEndpoint(final String path, final T servlet) {
        final Endpoint endpoint = new Endpoint(servlet);
        endpoint.setStrictPathEnabled(false);
        endpoint.setPath(path);

        _apiServer.addEndpoint(endpoint);
    }

    public ExplorerModule(final ExplorerProperties explorerProperties) {
        _explorerProperties = explorerProperties;

        final String tlsKeyFile = _explorerProperties.getTlsKeyFile();
        final String tlsCertificateFile = _explorerProperties.getTlsCertificateFile();
        if ( (tlsKeyFile != null) && (tlsCertificateFile != null) ) {
            _apiServer.setTlsPort(_explorerProperties.getTlsPort());
            _apiServer.setCertificate(_explorerProperties.getTlsCertificateFile(), _explorerProperties.getTlsKeyFile());
            _apiServer.enableEncryption(true);
            _apiServer.redirectToTls(false);
        }

        _apiServer.setPort(_explorerProperties.getPort());

        _announcementsApi = new AnnouncementsApi(_explorerProperties);

        { // Api Endpoints
            final String apiRootPath = "/api";
            final Environment environment = new Environment(_explorerProperties, _threadPool);

            { // Api v1
                final String v1ApiPrePath = (apiRootPath + "/v1");

                _assignEndpoint((v1ApiPrePath + "/search"), new SearchApi(v1ApiPrePath, environment));
                _assignEndpoint((v1ApiPrePath + "/blocks"), new BlocksApi(v1ApiPrePath, environment));
                _assignEndpoint((v1ApiPrePath + "/transactions"), new TransactionsApi(v1ApiPrePath, environment));
                _assignEndpoint((v1ApiPrePath + "/double-spend-proofs"), new DoubleSpendProofsApi(v1ApiPrePath, environment));
                _assignEndpoint((v1ApiPrePath + "/status"), new StatusApi(v1ApiPrePath, environment));
                _assignEndpoint((v1ApiPrePath + "/nodes"), new NodesApi(v1ApiPrePath, environment));
                _assignEndpoint((v1ApiPrePath + "/blockchain"), new BlockchainApi(v1ApiPrePath, environment));
                _assignEndpoint((v1ApiPrePath + "/slp"), new SlpApi(v1ApiPrePath, environment));

                { // WebSocket
                    final WebSocketEndpoint endpoint = new WebSocketEndpoint(_announcementsApi);
                    endpoint.setPath((v1ApiPrePath + "/announcements"));
                    endpoint.setStrictPathEnabled(true);
                    _apiServer.addEndpoint(endpoint);
                }
            }
        }

        { // Static Content
            final File servedDirectory = new File(_explorerProperties.getRootDirectory() +"/");
            final DirectoryServlet indexServlet = new DirectoryServlet(servedDirectory);
            indexServlet.setShouldServeDirectories(true);
            indexServlet.setIndexFile("index.html");
            indexServlet.setCacheEnabled(TimeUnit.DAYS.toSeconds(1L));

            final Endpoint endpoint = new Endpoint(indexServlet);
            endpoint.setPath("/");
            endpoint.setStrictPathEnabled(false);
            _apiServer.addEndpoint(endpoint);
        }
    }

    public void start() {
        _threadPool.start();
        _apiServer.start();
        _announcementsApi.start();
    }

    public void stop() {
        _announcementsApi.stop();
        _apiServer.stop();
        _threadPool.stop();
    }

    public void loop() {
        while (! Thread.interrupted()) {
            try { Thread.sleep(10000L); } catch (final Exception exception) { break; }
        }
    }
}