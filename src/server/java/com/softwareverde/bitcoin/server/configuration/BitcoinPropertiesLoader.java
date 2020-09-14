package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.util.Util;

import java.util.Properties;

public class BitcoinPropertiesLoader {
    public static BitcoinProperties loadBitcoinProperties(final Properties properties) {
        final BitcoinProperties bitcoinProperties = new BitcoinProperties();
        bitcoinProperties._bitcoinPort = Util.parseInt(properties.getProperty("bitcoin.port", BitcoinConstants.getDefaultNetworkPort().toString()));
        bitcoinProperties._bitcoinRpcPort = Util.parseInt(properties.getProperty("bitcoin.rpcPort", BitcoinConstants.getDefaultRpcPort().toString()));

        { // Parse Seed Nodes...
            final NodeProperties[] nodeProperties = PropertiesUtil.parseSeedNodeProperties("bitcoin.seedNodes", "[\"btc.softwareverde.com\", \"bitcoinverde.org\"]", properties);
            bitcoinProperties._seedNodeProperties = new ImmutableList<NodeProperties>(nodeProperties);
        }

        { // Parse DNS Seed Nodes...
            final String defaultSeedsString = "[\"seed.flowee.cash\", \"seed-bch.bitcoinforks.org\", \"btccash-seeder.bitcoinunlimited.info\", \"seed.bchd.cash\"]";
            final Json seedNodesJson = Json.parse(properties.getProperty("bitcoin.dnsSeeds", defaultSeedsString));

            final MutableList<String> dnsSeeds = new MutableList<String>(seedNodesJson.length());
            for (int i = 0; i < seedNodesJson.length(); ++i) {
                final String seedHost = seedNodesJson.getString(i);
                if (seedHost != null) {
                    dnsSeeds.add(seedHost);
                }
            }

            bitcoinProperties._dnsSeeds = dnsSeeds;
        }

        { // Parse TestNet Seed Nodes...
            final NodeProperties[] nodeProperties = PropertiesUtil.parseSeedNodeProperties("bitcoin.testNetSeedNodes", "[]", properties);
            bitcoinProperties._testNetSeedNodeProperties = new ImmutableList<NodeProperties>(nodeProperties);
        }

        { // Parse TestNet DNS Seed Nodes...
            final String defaultSeedsString = "[\"testnet-seed-bch.bitcoinforks.org\", \"testnet-seed.bchd.cash\"]";
            final Json seedNodesJson = Json.parse(properties.getProperty("bitcoin.testNetDnsSeeds", defaultSeedsString));

            final MutableList<String> dnsSeeds = new MutableList<String>(seedNodesJson.length());
            for (int i = 0; i < seedNodesJson.length(); ++i) {
                final String seedHost = seedNodesJson.getString(i);
                if (seedHost != null) {
                    dnsSeeds.add(seedHost);
                }
            }

            bitcoinProperties._testNetDnsSeeds = dnsSeeds;
        }

        { // Parse Whitelisted Nodes...
            final NodeProperties[] nodeProperties = PropertiesUtil.parseSeedNodeProperties("bitcoin.whitelistedNodes", "[]", properties);
            bitcoinProperties._whitelistedNodes = new ImmutableList<NodeProperties>(nodeProperties);
        }

        bitcoinProperties._banFilterIsEnabled = Util.parseBool(properties.getProperty("bitcoin.enableBanFilter", "1"));
        bitcoinProperties._maxPeerCount = Util.parseInt(properties.getProperty("bitcoin.maxPeerCount", "24"));
        bitcoinProperties._maxThreadCount = Util.parseInt(properties.getProperty("bitcoin.maxThreadCount", "4"));
        bitcoinProperties._trustedBlockHeight = Util.parseLong(properties.getProperty("bitcoin.trustedBlockHeight", "0"));
        bitcoinProperties._shouldSkipNetworking = Util.parseBool(properties.getProperty("bitcoin.skipNetworking", "0"));
        bitcoinProperties._deletePendingBlocksIsEnabled = Util.parseBool(properties.getProperty("bitcoin.deletePendingBlocks", "1"));
        bitcoinProperties._maxUtxoCacheByteCount = Util.parseLong(properties.getProperty("bitcoin.maxUtxoCacheByteCount", String.valueOf(UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT * UnspentTransactionOutputDatabaseManager.BYTES_PER_UTXO)));
        bitcoinProperties._utxoCommitFrequency = Util.parseLong(properties.getProperty("bitcoin.utxoCommitFrequency", "2016"));
        bitcoinProperties._logDirectory = properties.getProperty("bitcoin.logDirectory", "logs");
        bitcoinProperties._logLevel = LogLevel.fromString(properties.getProperty("bitcoin.logLevel", "INFO"));

        bitcoinProperties._utxoPurgePercent = Util.parseFloat(properties.getProperty("bitcoin.utxoPurgePercent", String.valueOf(UnspentTransactionOutputDatabaseManager.DEFAULT_PURGE_PERCENT)));
        if (bitcoinProperties._utxoPurgePercent < 0F) {
            bitcoinProperties._utxoPurgePercent = 0F;
        }
        else if (bitcoinProperties._utxoPurgePercent > 1F) {
            bitcoinProperties._utxoPurgePercent = 1F;
        }

        bitcoinProperties._bootstrapIsEnabled = Util.parseBool(properties.getProperty("bitcoin.enableBootstrap", "1"));
        bitcoinProperties._indexingModeIsEnabled = Util.parseBool(properties.getProperty("bitcoin.indexBlocks", "1"));
        bitcoinProperties._maxMessagesPerSecond = Util.parseInt(properties.getProperty("bitcoin.maxMessagesPerSecondPerNode", "250"));
        bitcoinProperties._dataDirectory = properties.getProperty("bitcoin.dataDirectory", "data");
        bitcoinProperties._shouldRelayInvalidSlpTransactions = Util.parseBool(properties.getProperty("bitcoin.relayInvalidSlpTransactions", "1"));

        bitcoinProperties._testNet = Util.parseInt(properties.getProperty("bitcoin.testNet", "0"));
        bitcoinProperties._testNetBitcoinPort = Util.parseInt(properties.getProperty("bitcoin.testNetPort", BitcoinConstants.TestNet.defaultNetworkPort.toString()));

        return bitcoinProperties;
    }

    protected BitcoinPropertiesLoader() { }
}
