package com.softwareverde.bitcoin.server.module.explorer;

import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.*;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.endpoint.WebSocketEndpoint;
import com.softwareverde.http.server.servlet.DirectoryServlet;
import com.softwareverde.http.server.servlet.Servlet;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class ExplorerModule {
    protected final HttpServer _apiServer = new HttpServer();
    protected final ThreadPool _threadPool = new MainThreadPool(512, 1000L);
    protected final ExplorerProperties _explorerProperties;
    protected final AnnouncementsApi _announcementsApi;

    protected <T extends Servlet> void _assignEndpoint(final String path, final T servlet) {
        final Endpoint endpoint = new Endpoint(servlet);
        endpoint.setStrictPathEnabled(true);
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
            _assignEndpoint("/api/v1/search", new SearchApi(_explorerProperties, _threadPool));
            _assignEndpoint("/api/v1/blocks", new BlocksApi(_explorerProperties, _threadPool));
            _assignEndpoint("/api/v1/transactions", new TransactionsApi(_explorerProperties, _threadPool));
            _assignEndpoint("/api/v1/status", new StatusApi(_explorerProperties, _threadPool));
            _assignEndpoint("/api/v1/nodes", new NodesApi(_explorerProperties, _threadPool));
            _assignEndpoint("/api/v1/blockchain", new BlockchainApi(_explorerProperties, _threadPool));
        }

        { // WebSocket
            final WebSocketEndpoint endpoint = new WebSocketEndpoint(_announcementsApi);
            endpoint.setPath("/api/v1/announcements");
            endpoint.setStrictPathEnabled(true);
            _apiServer.addEndpoint(endpoint);
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
        _apiServer.start();
    }

    public void stop() {
        _apiServer.stop();
        _announcementsApi.shutdown();
    }

    public void loop() {
        while (! Thread.interrupted()) {
            try { Thread.sleep(10000L); } catch (final Exception exception) { break; }
        }
    }
}