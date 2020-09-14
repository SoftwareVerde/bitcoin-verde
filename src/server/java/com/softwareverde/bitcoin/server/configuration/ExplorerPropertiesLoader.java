package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.util.Util;

import java.util.Properties;

public class ExplorerPropertiesLoader {
    public static ExplorerProperties loadExplorerProperties(final Properties properties) {
        final Integer port = Util.parseInt(properties.getProperty("explorer.httpPort", ExplorerProperties.HTTP_PORT.toString()));
        final String rootDirectory = properties.getProperty("explorer.rootDirectory", "explorer/www");

        final String bitcoinRpcUrl = properties.getProperty("explorer.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(properties.getProperty("explorer.bitcoinRpcPort", BitcoinConstants.MainNet.defaultRpcPort.toString()));

        final String stratumRpcUrl = properties.getProperty("explorer.stratumRpcUrl", "");
        final Integer stratumRpcPort = Util.parseInt(properties.getProperty("explorer.stratumRpcPort", StratumProperties.RPC_PORT.toString()));

        final Integer tlsPort = Util.parseInt(properties.getProperty("explorer.tlsPort", ExplorerProperties.TLS_PORT.toString()));
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
