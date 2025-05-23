package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeStore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanFilterCore implements BanFilter {
    public static class BanCriteria {
        public static final Integer FAILED_CONNECTION_ATTEMPT_COUNT = 10;
        public static final Long FAILED_CONNECTION_ATTEMPT_SECONDS_SPAN = 20L;

        public static final List<Sha256Hash> INVALID_BLOCKS = new ImmutableList<>(
            Sha256Hash.fromHexString("0000000000000000005CCD563C9ED7212AD591467CD3DB71A17D44918B687F34"),   // BTC Block 504031
            Sha256Hash.fromHexString("000000000000000001D956714215D96FFC00E0AFDA4CD0A96C96F8D802B1662B")    // BSV Block 556767
        );
    }

    protected static final Long DEFAULT_BAN_DURATION = (60L * 60L); // 1 Hour (in seconds)...

    protected final SystemTime _systemTime = new SystemTime();
    protected final BitcoinNodeStore _bitcoinNodeStore;
    protected final HashSet<Ip> _whitelist = new HashSet<>();
    protected final HashSet<Pattern> _blacklist = new HashSet<>();
    protected Long _banDurationInSeconds = DEFAULT_BAN_DURATION;

    protected Boolean _shouldBanIp(final Ip ip) {
        final Long sinceTimestamp = (_systemTime.getCurrentTimeInSeconds() - BanCriteria.FAILED_CONNECTION_ATTEMPT_SECONDS_SPAN);
        final Integer failedConnectionCount = 0; // TODO: _bitcoinNodeStore.getFailedConnectionCountForIp(ip, sinceTimestamp);
        final boolean shouldBanIp = (failedConnectionCount >= BanCriteria.FAILED_CONNECTION_ATTEMPT_COUNT);

        if (shouldBanIp) {
            Logger.debug("Ip (" + ip + ") failed to connect " + failedConnectionCount + " times within " + BanCriteria.FAILED_CONNECTION_ATTEMPT_SECONDS_SPAN + "s.");
        }

        return shouldBanIp;
    }

    public BanFilterCore(final BitcoinNodeStore bitcoinNodeStore) {
        _bitcoinNodeStore = bitcoinNodeStore;
    }

    @Override
    public Boolean isIpBanned(final Ip ip) {
        if (_whitelist.contains(ip)) {
            Logger.debug("IP is Whitelisted: " + ip);
            return false;
        }

        return _bitcoinNodeStore.isBanned(ip);
    }

    @Override
    public void banIp(final Ip ip) {
        _bitcoinNodeStore.banIp(ip);
    }

    @Override
    public void unbanIp(final Ip ip) {
        _bitcoinNodeStore.unbanIp(ip);
    }

    @Override
    public void onNodeConnected(final Ip ip) {
        // Nothing.
    }

    @Override
    public void onNodeHandshakeComplete(final BitcoinNode bitcoinNode) {
        final String userAgent = bitcoinNode.getUserAgent();
        if (userAgent == null) { return; }

        for (final Pattern pattern : _blacklist) {
            final Matcher matcher = pattern.matcher(userAgent);
            if (matcher.find()) {
                final Ip ip = bitcoinNode.getIp();
                _bitcoinNodeStore.banIp(ip);

                bitcoinNode.disconnect();
            }
        }
    }

    @Override
    public void onNodeDisconnected(final Ip ip) {
        if (_shouldBanIp(ip)) {
            _bitcoinNodeStore.banIp(ip);
        }
    }

    @Override
    public Boolean onInventoryReceived(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockInventory) {
        for (final Sha256Hash blockHash : BanCriteria.INVALID_BLOCKS) {
            final boolean containsInvalidBlock = blockInventory.contains(blockHash);
            if (containsInvalidBlock) {
                final Ip ip = bitcoinNode.getIp();
                _bitcoinNodeStore.banIp(ip);

                return false;
            }
        }

        return true;
    }

    @Override
    public Boolean onHeadersReceived(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
        for (final BlockHeader blockHeader : blockHeaders) {
            final Sha256Hash blockHash = blockHeader.getHash();
            final boolean containsInvalidBlock = BanCriteria.INVALID_BLOCKS.contains(blockHash);
            if (containsInvalidBlock) {
                final Ip ip = bitcoinNode.getIp();
                _bitcoinNodeStore.banIp(ip);

                return false;
            }
        }

        return true;
    }

    @Override
    public void addToWhitelist(final Ip ip) {
        _whitelist.add(ip);

        _bitcoinNodeStore.unbanIp(ip);

        Logger.debug("Added ip to Whitelist: " + ip);
    }

    @Override
    public void removeIpFromWhitelist(final Ip ip) {
        _whitelist.remove(ip);

        Logger.debug("Removed ip from Whitelist: " + ip);
    }

    @Override
    public void addToUserAgentBlacklist(final Pattern pattern) {
        _blacklist.add(pattern);
    }

    @Override
    public void removePatternFromUserAgentBlacklist(final Pattern pattern) {
        _blacklist.remove(pattern);
    }

    public void setBanDuration(final Long banDurationInSeconds) {
        _banDurationInSeconds = banDurationInSeconds;
    }
}
