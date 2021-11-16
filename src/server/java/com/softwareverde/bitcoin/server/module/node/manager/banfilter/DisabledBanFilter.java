package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.network.ip.Ip;

import java.util.regex.Pattern;

public class DisabledBanFilter implements BanFilter {

    @Override
    public Boolean isIpBanned(final Ip ip) {
        return false;
    }

    @Override
    public void banIp(final Ip ip) {
        // Nothing.
    }

    @Override
    public void unbanIp(final Ip ip) {
        // Nothing.
    }

    @Override
    public void addToWhitelist(final Ip ip) {
        // Nothing.
    }

    @Override
    public void removeIpFromWhitelist(final Ip ip) {
        // Nothing.
    }

    @Override
    public void addToUserAgentBlacklist(final Pattern pattern) {
        // Nothing.
    }

    @Override
    public void removePatternFromUserAgentBlacklist(final Pattern pattern) {
        // Nothing.
    }

    @Override
    public void onNodeConnected(final Ip ip) {
        // Nothing.
    }

    @Override
    public void onNodeHandshakeComplete(final BitcoinNode bitcoinNode) {
        // Nothing.
    }

    @Override
    public void onNodeDisconnected(final Ip ip) {
        // Nothing.
    }

    @Override
    public Boolean onInventoryReceived(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockInventory) {
        return true;
    }

    @Override
    public Boolean onHeadersReceived(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
        return true;
    }
}
