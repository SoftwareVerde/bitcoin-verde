package com.softwareverde.bitcoin.server.module.proxy;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.WebSocketEndpoint;
import com.softwareverde.http.server.servlet.ProxyServlet;
import com.softwareverde.http.websocket.WebSocket;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.util.HashMap;

public class ProxyModule {
    public static void execute(final String configurationFileName) {
        final ProxyModule stratumModule = new ProxyModule(configurationFileName);
        stratumModule.loop();
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected final SystemTime _systemTime = new SystemTime();

    protected final Configuration _configuration;
    protected final HttpServer _apiServer = new HttpServer();

    protected HashMap<Long, WebSocket> _proxiedWebsockets = new HashMap<Long, WebSocket>();

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile = new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected Boolean _addCertificate(final String tlsCertificateFile, final String tlsKeyFile) {
        if ( (tlsKeyFile != null) && (tlsCertificateFile != null) ) {
            _apiServer.addCertificate(tlsCertificateFile, tlsKeyFile);
            return true;
        }
        return false;
    }

    public ProxyModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.ProxyProperties proxyProperties = _configuration.getProxyProperties();
        final Configuration.StratumProperties stratumProperties = _configuration.getStratumProperties();
        final Configuration.ExplorerProperties explorerProperties = _configuration.getExplorerProperties();

        final String[] tlsKeyFiles = proxyProperties.getTlsKeyFiles();
        final String[] tlsCertificateFiles = proxyProperties.getTlsCertificateFiles();
        if (tlsKeyFiles.length != tlsCertificateFiles.length) {
            _printError("TLS Key/Certificate count mismatch.");
            BitcoinUtil.exitFailure();
        }

        boolean certificateWasAdded = false;
        for (int i = 0; i < tlsKeyFiles.length; ++i) {
            final String tlsKeyFile = tlsKeyFiles[i];
            final String tlsCertificateFile = tlsCertificateFiles[i];
            certificateWasAdded |= _addCertificate(tlsCertificateFile, tlsKeyFile);
        }

        if (certificateWasAdded) {
            _apiServer.setTlsPort(proxyProperties.getTlsPort());
            _apiServer.enableEncryption(true);
            _apiServer.redirectToTls(true, proxyProperties.getExternalTlsPort());
        }

        _apiServer.setPort(proxyProperties.getHttpPort());

        final HashMap<String, ProxyServlet.Url> proxyConfiguration = new HashMap<String, ProxyServlet.Url>();
        proxyConfiguration.put("^pool\\..*$", new ProxyServlet.Url("http", "localhost", stratumProperties.getHttpPort()));
        proxyConfiguration.put(".*", new ProxyServlet.Url("http", "localhost", explorerProperties.getPort()));

        final WebSocketEndpoint endpoint = new WebSocketEndpoint(new ProxyServlet(proxyConfiguration));
        endpoint.setStrictPathEnabled(false);
        endpoint.setPath("/");
        _apiServer.addEndpoint(endpoint);
    }

    public void loop() {
        _apiServer.start();

        while (true) {
            try { Thread.sleep(60000L); } catch (final Exception exception) { break; }
        }

        _apiServer.stop();
    }
}