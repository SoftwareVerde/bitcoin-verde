package com.softwareverde.bitcoin.server.module.explorer;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.BlocksApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.NodesApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.SearchApi;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.StatusApi;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.httpserver.DirectoryServlet;
import com.softwareverde.httpserver.HttpServer;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.Endpoint;
import com.softwareverde.servlet.Servlet;

import java.io.File;

public class ExplorerModule {
    public static void execute(final String configurationFileName) {
        final ExplorerModule explorerModule = new ExplorerModule(configurationFileName);
        explorerModule.start();
        explorerModule.loop();
        explorerModule.stop();
    }

    protected final HttpServer _apiServer = new HttpServer();
    protected final Configuration.ExplorerProperties _explorerProperties;

    protected <T extends Servlet> void _assignEndpoint(final String path, final T servlet) {
        final Endpoint endpoint = new Endpoint(servlet);
        endpoint.setStrictPathEnabled(true);
        endpoint.setPath(path);

        _apiServer.addEndpoint(endpoint);
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected ExplorerModule(final String configurationFilename) {
        final Configuration configuration = _loadConfigurationFile(configurationFilename);
        _explorerProperties = configuration.getExplorerProperties();

        _apiServer.setPort(_explorerProperties.getPort());
        // _apiServer.setTlsPort(_explorerProperties.getTlsPort());
        // _apiServer.setCertificate(_explorerProperties.getTlsCertificateFile(), _explorerProperties.getTlsKeyFile());
        // _apiServer.enableEncryption(true);
        // _apiServer.redirectToTls(true);

        { // Account Api
            _assignEndpoint("/api/v1/search", new SearchApi(_explorerProperties));
            _assignEndpoint("/api/v1/blocks", new BlocksApi(_explorerProperties));
            _assignEndpoint("/api/v1/status", new StatusApi(_explorerProperties));
            _assignEndpoint("/api/v1/nodes", new NodesApi(_explorerProperties));
        }

        { // Static Content
            final File servedDirectory = new File(_explorerProperties.getRootDirectory() +"/");
            final DirectoryServlet indexServlet = new DirectoryServlet(servedDirectory);
            indexServlet.setShouldServeDirectories(true);
            indexServlet.setIndexFile("index.html");

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
    }

    public void loop() {
        while (! Thread.interrupted()) {
            try { Thread.sleep(10000L); } catch (final Exception exception) { break; }
        }
    }
}