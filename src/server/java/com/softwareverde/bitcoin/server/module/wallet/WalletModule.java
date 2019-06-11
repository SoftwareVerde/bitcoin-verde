package com.softwareverde.bitcoin.server.module.wallet;

import com.softwareverde.bitcoin.server.configuration.WalletProperties;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.servlet.DirectoryServlet;
import com.softwareverde.http.server.servlet.Servlet;

import java.io.File;

public class WalletModule {
    protected final HttpServer _apiServer = new HttpServer();
    protected final WalletProperties _walletProperties;

    protected <T extends Servlet> void _assignEndpoint(final String path, final T servlet) {
        final Endpoint endpoint = new Endpoint(servlet);
        endpoint.setStrictPathEnabled(true);
        endpoint.setPath(path);

        _apiServer.addEndpoint(endpoint);
    }

    public WalletModule(final WalletProperties walletProperties) {
        _walletProperties = walletProperties;

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