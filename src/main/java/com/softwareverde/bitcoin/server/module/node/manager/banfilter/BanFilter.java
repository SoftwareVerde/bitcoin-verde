package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
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

    /**
     * Returns false if bad inventory was received and marks the node as banned.
     */
    Boolean onInventoryReceived(BitcoinNode bitcoinNode, List<Sha256Hash> blockInventory);

    /**
     * Returns false if bad block headers were received and marks the node as banned.
     */
    Boolean onHeadersReceived(BitcoinNode bitcoinNode, List<BlockHeader> blockHeaders);
}
