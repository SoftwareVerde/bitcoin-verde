package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.util.Util;

import java.util.Properties;

public class StratumPropertiesLoader {
    public static StratumProperties loadStratumProperties(final Properties properties) {
        final Integer port = Util.parseInt(properties.getProperty("stratum.port", StratumProperties.PORT.toString()));
        final Integer rpcPort = Util.parseInt(properties.getProperty("stratum.rpcPort", StratumProperties.RPC_PORT.toString()));
        final String bitcoinRpcUrl = properties.getProperty("stratum.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(properties.getProperty("stratum.bitcoinRpcPort", BitcoinConstants.MainNet.defaultRpcPort.toString()));
        final Integer httpPort = Util.parseInt(properties.getProperty("stratum.httpPort", StratumProperties.HTTP_PORT.toString()));
        final Integer tlsPort = Util.parseInt(properties.getProperty("stratum.tlsPort", StratumProperties.TLS_PORT.toString()));
        final String rootDirectory = properties.getProperty("stratum.rootDirectory", "stratum/www");
        final String tlsKeyFile = properties.getProperty("stratum.tlsKeyFile", "");
        final String tlsCertificateFile = properties.getProperty("stratum.tlsCertificateFile", "");
        final String cookiesDirectory = properties.getProperty("stratum.cookiesDirectory", "tmp");

        final StratumProperties stratumProperties = new StratumProperties();
        stratumProperties._port = port;
        stratumProperties._rpcPort = rpcPort;
        stratumProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        stratumProperties._bitcoinRpcPort = bitcoinRpcPort;

        stratumProperties._rootDirectory = rootDirectory;
        stratumProperties._httpPort = httpPort;
        stratumProperties._tlsPort = tlsPort;
        stratumProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        stratumProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);
        stratumProperties._cookiesDirectory = cookiesDirectory;

        return stratumProperties;
    }

    protected StratumPropertiesLoader() { }
}
