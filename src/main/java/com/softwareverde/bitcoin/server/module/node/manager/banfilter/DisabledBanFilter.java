package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.network.ip.Ip;

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
    public void addIpToWhitelist(final Ip ip) {
        // Nothing.
    }

    @Override
    public void removeIpFromWhitelist(final Ip ip) {
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
}
