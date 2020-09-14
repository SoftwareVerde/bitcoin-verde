package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.util.Util;

import java.util.Properties;

public class WalletPropertiesLoader {
    public static WalletProperties loadWalletProperties(final Properties properties) {
        final Integer port = Util.parseInt(properties.getProperty("wallet.port", String.valueOf(WalletProperties.PORT)));
        final String rootDirectory = properties.getProperty("wallet.rootDirectory", "wallet/www");

        final Integer tlsPort = Util.parseInt(properties.getProperty("wallet.tlsPort", String.valueOf(WalletProperties.TLS_PORT)));
        final String tlsKeyFile = properties.getProperty("wallet.tlsKeyFile", "");
        final String tlsCertificateFile = properties.getProperty("wallet.tlsCertificateFile", "");

        final WalletProperties walletProperties = new WalletProperties();
        walletProperties._port = port;
        walletProperties._rootDirectory = rootDirectory;

        walletProperties._tlsPort = tlsPort;
        walletProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        walletProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);

        return walletProperties;
    }

    protected WalletPropertiesLoader() { }
}
