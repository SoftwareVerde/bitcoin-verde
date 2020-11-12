package com.softwareverde.bitcoin.server.module.proxy;

import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.configuration.ProxyProperties;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.WebSocketEndpoint;
import com.softwareverde.http.server.servlet.ProxyServlet;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;

public class ProxyModule {
    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected final SystemTime _systemTime = new SystemTime();

    protected final ProxyProperties _proxyProperties;
    protected final StratumProperties _stratumProperties;
    protected final ExplorerProperties _explorerProperties;
    protected final HttpServer _apiServer = new HttpServer();

    protected Boolean _addCertificate(final String tlsCertificateFile, final String tlsKeyFile) {
        if ( (tlsKeyFile != null) && (tlsCertificateFile != null) ) {
            _apiServer.addCertificate(tlsCertificateFile, tlsKeyFile);
            return true;
        }
        return false;
    }

    public ProxyModule(final ProxyProperties proxyProperties, final StratumProperties stratumProperties, final ExplorerProperties explorerProperties) {
        _proxyProperties = proxyProperties;
        _stratumProperties = stratumProperties;
        _explorerProperties = explorerProperties;

        final List<String> tlsKeyFiles = proxyProperties.getTlsKeyFiles();
        final List<String> tlsCertificateFiles = proxyProperties.getTlsCertificateFiles();
        if (tlsKeyFiles.getCount() != tlsCertificateFiles.getCount()) {
            _printError("TLS Key/Certificate count mismatch.");
            BitcoinUtil.exitFailure();
        }

        boolean certificateWasAdded = false;
        for (int i = 0; i < tlsKeyFiles.getCount(); ++i) {
            final String tlsKeyFile = tlsKeyFiles.get(i);
            final String tlsCertificateFile = tlsCertificateFiles.get(i);
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