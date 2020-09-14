package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.util.Util;

import java.util.Properties;

public class ProxyPropertiesLoader {
    public static ProxyProperties loadProxyProperties(final Properties properties) {
        final Integer httpPort = Util.parseInt(properties.getProperty("proxy.httpPort", ProxyProperties.HTTP_PORT.toString()));
        final Integer tlsPort = Util.parseInt(properties.getProperty("proxy.tlsPort", ProxyProperties.TLS_PORT.toString()));
        final Integer externalTlsPort = Util.parseInt(properties.getProperty("proxy.externalTlsPort", tlsPort.toString()));

        final String[] tlsKeyFiles = PropertiesUtil.parseStringArrayProperty("proxy.tlsKeyFiles", "[]", properties);
        final String[] tlsCertificateFiles = PropertiesUtil.parseStringArrayProperty("proxy.tlsCertificateFiles", "[]", properties);

        final ProxyProperties proxyProperties = new ProxyProperties();
        proxyProperties._httpPort = httpPort;
        proxyProperties._tlsPort = tlsPort;
        proxyProperties._externalTlsPort = externalTlsPort;
        proxyProperties._tlsKeyFiles = tlsKeyFiles;
        proxyProperties._tlsCertificateFiles = tlsCertificateFiles;

        return proxyProperties;
    }

    protected ProxyPropertiesLoader() { }
}
