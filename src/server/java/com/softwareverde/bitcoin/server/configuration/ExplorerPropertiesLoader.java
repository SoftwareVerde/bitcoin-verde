package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.util.Util;

import java.util.Properties;

public class ExplorerPropertiesLoader {
    public static ExplorerProperties loadExplorerProperties(final Properties properties) {
        final Integer port = Util.parseInt(properties.getProperty("explorer.httpPort", String.valueOf(ExplorerProperties.HTTP_PORT)));
        final String rootDirectory = properties.getProperty("explorer.rootDirectory", "explorer/www");

        final String bitcoinRpcUrl = properties.getProperty("explorer.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(properties.getProperty("explorer.bitcoinRpcPort", null));

        final String stratumRpcUrl = properties.getProperty("explorer.stratumRpcUrl", "");
        final Integer stratumRpcPort = Util.parseInt(properties.getProperty("explorer.stratumRpcPort", String.valueOf(StratumProperties.RPC_PORT)));

        final Integer tlsPort = Util.parseInt(properties.getProperty("explorer.tlsPort", String.valueOf(ExplorerProperties.TLS_PORT)));
        final String tlsKeyFile = properties.getProperty("explorer.tlsKeyFile", "");
        final String tlsCertificateFile = properties.getProperty("explorer.tlsCertificateFile", "");

        final ExplorerProperties explorerProperties = new ExplorerProperties();
        explorerProperties._port = port;
        explorerProperties._rootDirectory = rootDirectory;

        explorerProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        explorerProperties._bitcoinRpcPort = bitcoinRpcPort;

        explorerProperties._stratumRpcUrl = stratumRpcUrl;
        explorerProperties._stratumRpcPort = stratumRpcPort;

        explorerProperties._tlsPort = tlsPort;
        explorerProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        explorerProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);

        return explorerProperties;
    }

    protected ExplorerPropertiesLoader() { }
}
