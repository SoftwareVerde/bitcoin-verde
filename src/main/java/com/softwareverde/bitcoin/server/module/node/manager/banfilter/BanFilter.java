package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.network.ip.Ip;

public interface BanFilter {
    Boolean isIpBanned(Ip ip);

    void banIp(Ip ip);
    void unbanIp(Ip ip);

    void addIpToWhitelist(Ip ip);
    void removeIpFromWhitelist(Ip ip);

    void onNodeConnected(Ip ip);
    void onNodeHandshakeComplete(BitcoinNode bitcoinNode);
    void onNodeDisconnected(Ip ip);
}
