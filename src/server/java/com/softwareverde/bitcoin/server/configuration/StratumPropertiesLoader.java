package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.util.Util;

import java.util.Properties;

public class StratumPropertiesLoader {
    public static StratumProperties loadStratumProperties(final Properties properties) {
        final Integer port = Util.parseInt(properties.getProperty("stratum.port", String.valueOf(StratumProperties.PORT)));
        final Integer rpcPort = Util.parseInt(properties.getProperty("stratum.rpcPort", String.valueOf(StratumProperties.RPC_PORT)));
        final String bitcoinRpcUrl = properties.getProperty("stratum.bitcoinRpcUrl", "");
        final String bitcoinRpcUsername = properties.getProperty("stratum.bitcoinRpcUsername", "");
        final String bitcoinRpcPassword = properties.getProperty("stratum.bitcoinRpcPassword", "");
        final Integer bitcoinRpcPort = Util.parseInt(properties.getProperty("stratum.bitcoinRpcPort", null));
        final Integer httpPort = Util.parseInt(properties.getProperty("stratum.httpPort", String.valueOf(StratumProperties.HTTP_PORT)));
        final Integer tlsPort = Util.parseInt(properties.getProperty("stratum.tlsPort", String.valueOf(StratumProperties.TLS_PORT)));
        final String rootDirectory = properties.getProperty("stratum.rootDirectory", "stratum/www");
        final String tlsKeyFile = properties.getProperty("stratum.tlsKeyFile", "");
        final String tlsCertificateFile = properties.getProperty("stratum.tlsCertificateFile", "");
        final String cookiesDirectory = properties.getProperty("stratum.cookiesDirectory", "tmp");
        final Boolean useSecureCookies = Util.parseBool(properties.getProperty("stratum.secureCookies", "1"));

        final StratumProperties stratumProperties = new StratumProperties();
        stratumProperties._port = port;
        stratumProperties._rpcPort = rpcPort;
        stratumProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        stratumProperties._bitcoinRpcPort = bitcoinRpcPort;
        stratumProperties._rpcCredentials = (Util.isBlank(bitcoinRpcUsername) ? null : new RpcCredentials(bitcoinRpcUsername, bitcoinRpcPassword));

        stratumProperties._rootDirectory = rootDirectory;
        stratumProperties._httpPort = httpPort;
        stratumProperties._tlsPort = tlsPort;
        stratumProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        stratumProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);
        stratumProperties._cookiesDirectory = cookiesDirectory;
        stratumProperties._useSecureCookies = useSecureCookies;

        return stratumProperties;
    }

    protected StratumPropertiesLoader() { }
}
