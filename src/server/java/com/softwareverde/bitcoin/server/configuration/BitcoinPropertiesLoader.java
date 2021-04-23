package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.util.Util;

import java.util.Properties;

public class BitcoinPropertiesLoader {
    public static BitcoinProperties loadBitcoinProperties(final Properties properties) {
        final BitcoinProperties bitcoinProperties = new BitcoinProperties();
        bitcoinProperties._bitcoinPort = Util.parseInt(properties.getProperty("bitcoin.port", null));
        bitcoinProperties._bitcoinRpcPort = Util.parseInt(properties.getProperty("bitcoin.rpcPort", null));

        { // Parse Seed Nodes...
            final String defaultSeedNodes = "[\"btc.softwareverde.com\", \"bitcoinverde.org\"]";
            bitcoinProperties._seedNodeProperties = PropertiesUtil.parseSeedNodeProperties("bitcoin.seedNodes", BitcoinConstants.MainNet.defaultNetworkPort, defaultSeedNodes, properties);
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
            final String defaultTestNetSeedNodes = "[]";
            bitcoinProperties._testNetSeedNodeProperties = PropertiesUtil.parseSeedNodeProperties("bitcoin.testNetSeedNodes", BitcoinConstants.TestNet.defaultNetworkPort, defaultTestNetSeedNodes, properties);
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

        { // Parse Blacklisted User Agents...
            final String defaultBlacklist = "[\".*Bitcoin ABC.*\", \".*Bitcoin SV.*\"]";
            final Json blacklistJson = Json.parse(properties.getProperty("bitcoin.userAgentBlacklist", defaultBlacklist));

            final int itemCount = blacklistJson.length();;
            final MutableList<String> patterns = new MutableList<String>(itemCount);
            for (int i = 0; i < itemCount; ++i) {
                final String pattern = blacklistJson.getString(i);
                patterns.add(pattern);
            }

            bitcoinProperties._userAgentBlacklist = patterns;
        }

        { // Parse Whitelisted Nodes...
            final String defaultNodeWhitelist = "[]";
            bitcoinProperties._nodeWhitelist = PropertiesUtil.parseSeedNodeProperties("bitcoin.whitelistedNodes", null, defaultNodeWhitelist, properties);
        }

        bitcoinProperties._banFilterIsEnabled = Util.parseBool(properties.getProperty("bitcoin.enableBanFilter", "1"));
        bitcoinProperties._minPeerCount = Util.parseInt(properties.getProperty("bitcoin.minPeerCount", "8"));
        bitcoinProperties._maxPeerCount = Util.parseInt(properties.getProperty("bitcoin.maxPeerCount", "24"));
        bitcoinProperties._maxThreadCount = Util.parseInt(properties.getProperty("bitcoin.maxThreadCount", "4"));
        bitcoinProperties._trustedBlockHeight = Util.parseLong(properties.getProperty("bitcoin.trustedBlockHeight", "0"));
        bitcoinProperties._shouldSkipNetworking = Util.parseBool(properties.getProperty("bitcoin.skipNetworking", "0"));
        bitcoinProperties._shouldPrioritizeNewPeers = Util.parseBool(properties.getProperty("bitcoin.prioritizeNewPeers", "0"));
        bitcoinProperties._deletePendingBlocksIsEnabled = Util.parseBool(properties.getProperty("bitcoin.deletePendingBlocks", "1"));

        final Long defaultMaxUtxoCacheByteCount = (UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT * UnspentTransactionOutputDatabaseManager.BYTES_PER_UTXO);
        bitcoinProperties._maxUtxoCacheByteCount = Util.parseLong(properties.getProperty("bitcoin.maxUtxoCacheByteCount", String.valueOf(defaultMaxUtxoCacheByteCount)));

        bitcoinProperties._utxoCommitFrequency = Util.parseLong(properties.getProperty("bitcoin.utxoCommitFrequency", "50000"));
        bitcoinProperties._logDirectory = properties.getProperty("bitcoin.logDirectory", "logs");
        bitcoinProperties._logLevel = LogLevel.fromString(properties.getProperty("bitcoin.logLevel", "INFO"));

        bitcoinProperties._utxoPurgePercent = Util.parseFloat(properties.getProperty("bitcoin.utxoPurgePercent", String.valueOf(UnspentTransactionOutputDatabaseManager.DEFAULT_PURGE_PERCENT)));
        if (bitcoinProperties._utxoPurgePercent < 0.05F) {
            bitcoinProperties._utxoPurgePercent = 0.05F;
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
        bitcoinProperties._testNetworkBitcoinPort = Util.parseInt(properties.getProperty("bitcoin.testNetPort", null));
        bitcoinProperties._testNetworkRpcPort = Util.parseInt(properties.getProperty("bitcoin.testNetRpcPort", null));

        return bitcoinProperties;
    }

    protected BitcoinPropertiesLoader() { }
}
