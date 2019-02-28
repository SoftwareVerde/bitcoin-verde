package com.softwareverde.bitcoin.server.module.wallet;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.httpserver.DirectoryServlet;
import com.softwareverde.httpserver.HttpServer;
import com.softwareverde.io.Logger;
import com.softwareverde.servlet.Endpoint;
import com.softwareverde.servlet.Servlet;

import java.io.File;

public class WalletModule {
    public static void execute(final String configurationFileName) {
        final WalletModule walletModule = new WalletModule(configurationFileName);
        walletModule.start();
        walletModule.loop();
        walletModule.stop();
    }

    protected final HttpServer _apiServer = new HttpServer();
    protected final Configuration.WalletProperties _walletProperties;

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

    protected WalletModule(final String configurationFilename) {
        final Configuration configuration = _loadConfigurationFile(configurationFilename);
        _walletProperties = configuration.getWalletProperties();

        final String tlsKeyFile = _walletProperties.getTlsKeyFile();
        final String tlsCertificateFile = _walletProperties.getTlsCertificateFile();
        if ( (tlsKeyFile != null) && (tlsCertificateFile != null) ) {
            _apiServer.setTlsPort(_walletProperties.getTlsPort());
            _apiServer.setCertificate(_walletProperties.getTlsCertificateFile(), _walletProperties.getTlsKeyFile());
            _apiServer.enableEncryption(true);
            _apiServer.redirectToTls(true);
        }

        _apiServer.setPort(_walletProperties.getPort());

        { // Static Content
            final File servedDirectory = new File(_walletProperties.getRootDirectory() +"/");
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