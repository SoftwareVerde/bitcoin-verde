package com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof;

import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class DoubleSpendProofAnnouncementHandlerFactory implements NodeInitializer.DoubleSpendProofAnnouncementHandlerFactory {
    public interface BitcoinNodeCollector {
        List<BitcoinNode> getConnectedNodes();
    }

    protected final DoubleSpendProofProcessor _doubleSpendProofProcessor;
    protected final DoubleSpendProofStore _doubleSpendProofStore;
    protected final BitcoinNodeCollector _bitcoinNodeCollector;

    protected void _downloadDoubleSpendProof(final Sha256Hash doubleSpendProofHash, final BitcoinNode bitcoinNode) {
        bitcoinNode.requestDoubleSpendProof(doubleSpendProofHash, new BitcoinNode.DownloadDoubleSpendProofCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final DoubleSpendProof doubleSpendProof) {
                Logger.info("DSProof Received: " + doubleSpendProof.getHash() + " from " + bitcoinNode);
                _onDoubleSpendProofDownloaded(doubleSpendProof, bitcoinNode);
            }
        });
    }

    protected void _onDoubleSpendProofDownloaded(final DoubleSpendProof doubleSpendProof, final BitcoinNode originatingBitcoinNode) {
        final Boolean isValidAndUnseen = _doubleSpendProofProcessor.processDoubleSpendProof(doubleSpendProof);
        if (isValidAndUnseen) { // Broadcast unseen proofs to other peers...
            final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
            Logger.debug("DoubleSpendProof validated: " + doubleSpendProofHash);

            final List<BitcoinNode> bitcoinNodes = _bitcoinNodeCollector.getConnectedNodes();
            for (final BitcoinNode bitcoinNode : bitcoinNodes) {
                if (Util.areEqual(originatingBitcoinNode, bitcoinNode)) { continue; }
                bitcoinNode.transmitDoubleSpendProofHash(doubleSpendProofHash);
            }
        }
    }

    public DoubleSpendProofAnnouncementHandlerFactory(final DoubleSpendProofProcessor doubleSpendProofProcessor, final DoubleSpendProofStore doubleSpendProofStore, final BitcoinNodeCollector bitcoinNodeCollector) {
        _doubleSpendProofProcessor = doubleSpendProofProcessor;
        _doubleSpendProofStore = doubleSpendProofStore;
        _bitcoinNodeCollector = bitcoinNodeCollector;
    }

    @Override
    public BitcoinNode.DoubleSpendProofAnnouncementHandler createDoubleSpendProofAnnouncementHandler(final BitcoinNode bitcoinNode) {
        return new BitcoinNode.DoubleSpendProofAnnouncementHandler() {
            @Override
            public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> doubleSpendProofsIdentifiers) {
                for (final Sha256Hash doubleSpendProofHash : doubleSpendProofsIdentifiers) {
                    final Boolean doubleSpendProofIsBanned = _doubleSpendProofStore.isDoubleSpendProofBanned(doubleSpendProofHash);
                    if (doubleSpendProofIsBanned) { continue; }

                    final boolean doubleSpendIsProcessed = (_doubleSpendProofStore.getDoubleSpendProof(doubleSpendProofHash) != null);
                    if (doubleSpendIsProcessed) { continue; }

                    _downloadDoubleSpendProof(doubleSpendProofHash, bitcoinNode);
                }
            }
        };
    }
}
