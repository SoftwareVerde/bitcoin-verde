package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.util.Util;

import java.util.Properties;

public class ElectrumPropertiesLoader {
    public static ElectrumProperties loadProperties(final Properties properties) {
        final String bitcoinRpcUrl = properties.getProperty("electrum.bitcoinRpcUrl", "localhost");
        final String bitcoinRpcUsername = properties.getProperty("electrum.bitcoinRpcUsername", "");
        final String bitcoinRpcPassword = properties.getProperty("electrum.bitcoinRpcPassword", "");
        final Integer bitcoinRpcPort = Util.parseInt(properties.getProperty("electrum.bitcoinRpcPort", String.valueOf(BitcoinProperties.RPC_PORT)));
        final Integer httpPort = Util.parseInt(properties.getProperty("electrum.httpPort", String.valueOf(ElectrumProperties.HTTP_PORT)));
        final Integer tlsPort = Util.parseInt(properties.getProperty("electrum.tlsPort", String.valueOf(ElectrumProperties.TLS_PORT)));
        final String tlsKeyFile = properties.getProperty("electrum.tlsKeyFile", "");
        final String tlsCertificateFile = properties.getProperty("electrum.tlsCertificateFile", "");

        final ElectrumProperties electrumProperties = new ElectrumProperties();
        electrumProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        electrumProperties._bitcoinRpcPort = bitcoinRpcPort;
        electrumProperties._rpcCredentials = (Util.isBlank(bitcoinRpcUsername) ? null : new RpcCredentials(bitcoinRpcUsername, bitcoinRpcPassword));

        electrumProperties._httpPort = httpPort;
        electrumProperties._tlsPort = tlsPort;
        electrumProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        electrumProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);

        return electrumProperties;
    }

    protected ElectrumPropertiesLoader() { }
}
