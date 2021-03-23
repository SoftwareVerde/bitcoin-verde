package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration {
    protected final Properties _properties;

    protected final BitcoinProperties _bitcoinProperties;
    protected final BitcoinVerdeDatabaseProperties _bitcoinDatabaseProperties;

    protected final BitcoinVerdeDatabaseProperties _spvDatabaseProperties;

    protected final ExplorerProperties _explorerProperties;

    protected final StratumProperties _stratumProperties;
    protected final BitcoinVerdeDatabaseProperties _stratumDatabaseProperties;

    protected final WalletProperties _walletProperties;
    protected final ProxyProperties _proxyProperties;

    public Configuration() {
        this(null);
    }

    public Configuration(final File configurationFile) {
        _properties = new Properties();

        if (configurationFile != null) {
            try (final FileInputStream fileInputStream = new FileInputStream(configurationFile)) {
                _properties.load(fileInputStream);
            }
            catch (final IOException exception) {
                Logger.warn("Unable to load properties.");
            }
        }

        _bitcoinDatabaseProperties = DatabasePropertiesLoader.loadDatabaseProperties("bitcoin", _properties);
        _stratumDatabaseProperties = DatabasePropertiesLoader.loadDatabaseProperties("stratum", _properties);
        _spvDatabaseProperties = DatabasePropertiesLoader.loadDatabaseProperties("spv", _properties);

        _bitcoinProperties = BitcoinPropertiesLoader.loadBitcoinProperties(_properties);
        _stratumProperties = StratumPropertiesLoader.loadStratumProperties(_properties);
        _explorerProperties = ExplorerPropertiesLoader.loadExplorerProperties(_properties);
        _walletProperties = WalletPropertiesLoader.loadWalletProperties(_properties);
        _proxyProperties = ProxyPropertiesLoader.loadProxyProperties(_properties);
    }

    public BitcoinProperties getBitcoinProperties() { return _bitcoinProperties; }
    public BitcoinVerdeDatabaseProperties getBitcoinDatabaseProperties() { return _bitcoinDatabaseProperties; }

    public BitcoinVerdeDatabaseProperties getSpvDatabaseProperties() { return _spvDatabaseProperties; }

    public ExplorerProperties getExplorerProperties() { return _explorerProperties; }

    public StratumProperties getStratumProperties() { return _stratumProperties; }
    public BitcoinVerdeDatabaseProperties getStratumDatabaseProperties() { return _stratumDatabaseProperties; }

    public WalletProperties getWalletProperties() { return _walletProperties; }
    public ProxyProperties getProxyProperties() { return _proxyProperties; }

}
