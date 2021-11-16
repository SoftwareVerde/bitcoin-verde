package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface BitcoinNodeObserver {
    default void onHandshakeComplete(BitcoinNode bitcoinNode) { }

    default void onPingReceived(BitcoinNode bitcoinNode) { }
    default void onPongReceived(BitcoinNode bitcoinNode, Long msElapsed) { }

    /**
     * Triggered when the remote peer requests data from the local peer.
     */
    default void onDataRequested(BitcoinNode bitcoinNode, MessageType expectedResponseType) { }
    default void onDataSent(BitcoinNode bitcoinNode, MessageType messageType, Integer byteCount) { }

    /**
     * Triggered when the remote peer sends data to the local peer.
     */
    default void onDataReceived(BitcoinNode bitcoinNode, MessageType messageType, Integer byteCount, Boolean wasRequested) { }
    default void onFailedRequest(BitcoinNode bitcoinNode, MessageType expectedResponseType, RequestPriority requestPriority) { }

    default void onBlockNotFound(BitcoinNode bitcoinNode, Sha256Hash blockHash) { }
    default void onTransactionNotFound(BitcoinNode bitcoinNode, Sha256Hash transactionHash) { }
}
