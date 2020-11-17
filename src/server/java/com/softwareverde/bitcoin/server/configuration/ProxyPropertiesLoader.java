package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

import java.util.Properties;

public class ProxyPropertiesLoader {
    public static ProxyProperties loadProxyProperties(final Properties properties) {
        final Integer httpPort = Util.parseInt(properties.getProperty("proxy.httpPort", String.valueOf(ProxyProperties.HTTP_PORT)));
        final Integer tlsPort = Util.parseInt(properties.getProperty("proxy.tlsPort", String.valueOf(ProxyProperties.TLS_PORT)));
        final Integer externalTlsPort = Util.parseInt(properties.getProperty("proxy.externalTlsPort", String.valueOf(tlsPort)));

        final List<String> tlsKeyFiles = PropertiesUtil.parseStringArrayProperty("proxy.tlsKeyFiles", "[]", properties);
        final List<String> tlsCertificateFiles = PropertiesUtil.parseStringArrayProperty("proxy.tlsCertificateFiles", "[]", properties);

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
