package com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofValidator;
import com.softwareverde.bitcoin.transaction.input.UnconfirmedTransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class DoubleSpendProofAnnouncementHandlerFactory implements NodeInitializer.DoubleSpendProofAnnouncementHandlerFactory {
    public interface BitcoinNodeCollector {
        List<BitcoinNode> getConnectedNodes();
    }

    protected final UpgradeSchedule _upgradeSchedule;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final DoubleSpendProofStore _doubleSpendProofStore;
    protected final BitcoinNodeCollector _bitcoinNodeCollector;

    protected Boolean _isDoubleSpendValid(final DoubleSpendProof doubleSpendProof) {
        final TransactionOutputIdentifier transactionOutputIdentifier = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();

        final DoubleSpendProofPreimage doubleSpendProofPreimage0 = doubleSpendProof.getDoubleSpendProofPreimage0();
        final DoubleSpendProofPreimage doubleSpendProofPreimage1 = doubleSpendProof.getDoubleSpendProofPreimage1();

        { // Ensure preimages are unique...
            final List<ByteArray> unlockingScriptData0 = doubleSpendProofPreimage0.getUnlockingScriptPushData();
            final List<ByteArray> unlockingScriptData1 = doubleSpendProofPreimage1.getUnlockingScriptPushData();
            if (Util.areEqual(unlockingScriptData0, unlockingScriptData1)) { return false; }
        }

        { // Ensure the preimages are in the correct/canonical order...
            final Sha256Hash transactionOutputsDigest0 = doubleSpendProofPreimage0.getTransactionOutputsDigest();
            final Sha256Hash transactionOutputsDigest1 = doubleSpendProofPreimage1.getTransactionOutputsDigest();
            if (transactionOutputsDigest0.compareTo(transactionOutputsDigest1) > 0) { return false; }

            final Sha256Hash previousOutputsDigest0 = doubleSpendProofPreimage0.getPreviousOutputsDigest();
            final Sha256Hash previousOutputsDigest1 = doubleSpendProofPreimage1.getPreviousOutputsDigest();
            if (previousOutputsDigest0.compareTo(previousOutputsDigest1) > 0) { return false; }
        }

        // NOTE: This check is disabled since it is performed during storing the proof.
        //  If the lookup wasn't O(N) then the duplicate check wouldn't be a problem.
        //
        // { // Ensure the DoubleSpendProof is unique / is not redundant with an existing DoubleSpendProof...
        //     final DoubleSpendProof redundantDoubleSpendProof = _doubleSpendProofStore.getDoubleSpendProof(transactionOutputIdentifier);
        //     if (redundantDoubleSpendProof != null) { return false; }
        // }

        final Transaction transactionBeingSpent;
        final Transaction conflictingTransaction;
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            { // Load the Transaction being spent from the database...
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                if (transactionId == null) { return false; }

                transactionBeingSpent = transactionDatabaseManager.getTransaction(transactionId);
            }

            { // Load the conflicting Transaction from the mempool...
                final UnconfirmedTransactionInputDatabaseManager transactionInputDatabaseManager = databaseManager.getUnconfirmedTransactionInputDatabaseManager();
                final UnconfirmedTransactionInputId unconfirmedTransactionInputId = transactionInputDatabaseManager.getUnconfirmedTransactionInputIdSpendingTransactionOutput(transactionOutputIdentifier);
                if (unconfirmedTransactionInputId == null) { return false; }

                final TransactionId transactionId = transactionInputDatabaseManager.getTransactionId(unconfirmedTransactionInputId);
                conflictingTransaction = transactionDatabaseManager.getTransaction(transactionId);
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return false;
        }

        if ( (transactionBeingSpent == null) || (conflictingTransaction == null) ) { return false; }

        final TransactionOutput transactionOutputBeingSpent;
        {
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
            final List<TransactionOutput> transactionOutputs = transactionBeingSpent.getTransactionOutputs();
            if (transactionOutputIndex >= transactionOutputs.getCount()) { return false; }

            transactionOutputBeingSpent = transactionOutputs.get(transactionOutputIndex);
        }

        final DoubleSpendProofValidator doubleSpendProofValidator = new DoubleSpendProofValidator(_upgradeSchedule);

        final Boolean firstProofIsValid = doubleSpendProofValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutputBeingSpent, conflictingTransaction, doubleSpendProofPreimage0);
        if (! firstProofIsValid) { return false; }

        return doubleSpendProofValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutputBeingSpent, conflictingTransaction, doubleSpendProofPreimage1);
    }

    protected void _downloadDoubleSpendProof(final Sha256Hash doubleSpendProofHash, final BitcoinNode bitcoinNode) {
        bitcoinNode.requestDoubleSpendProof(doubleSpendProofHash, new BitcoinNode.DownloadDoubleSpendProofCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode originatingBitcoinNode, final DoubleSpendProof doubleSpendProof) {
                _onDoubleSpendProofDownloaded(doubleSpendProof, originatingBitcoinNode);
            }
        });
    }

    protected void _onDoubleSpendProofDownloaded(final DoubleSpendProof doubleSpendProof, final BitcoinNode originatingBitcoinNode) {
        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
        final Boolean doubleSpendProofIsValid = _isDoubleSpendValid(doubleSpendProof);
        if (! doubleSpendProofIsValid) {
            // TODO: Consider avoiding reprocessing previously failed proofs (although it is not trivial, since the previous proof
            //  may have failed because the original transaction may not have been received yet).
            Logger.debug("Invalid DoubleSpendProof from " + originatingBitcoinNode + ": " + doubleSpendProofHash);
            return;
        }

        final Boolean wasUnseen = _doubleSpendProofStore.storeDoubleSpendProof(doubleSpendProof);

        if (wasUnseen) { // Broadcast unseen proofs to other peers...
            Logger.info("DoubleSpendProof received: " + doubleSpendProofHash);

            final List<BitcoinNode> bitcoinNodes = _bitcoinNodeCollector.getConnectedNodes();
            for (final BitcoinNode bitcoinNode : bitcoinNodes) {
                if (Util.areEqual(originatingBitcoinNode, bitcoinNode)) { continue; }
                bitcoinNode.transmitDoubleSpendProofHash(doubleSpendProofHash);
            }
        }
    }

    public DoubleSpendProofAnnouncementHandlerFactory(final FullNodeDatabaseManagerFactory databaseManagerFactory, final DoubleSpendProofStore doubleSpendProofStore, final UpgradeSchedule upgradeSchedule, final BitcoinNodeCollector bitcoinNodeCollector) {
        _doubleSpendProofStore = doubleSpendProofStore;
        _bitcoinNodeCollector = bitcoinNodeCollector;
        _databaseManagerFactory = databaseManagerFactory;
        _upgradeSchedule = upgradeSchedule;
    }

    @Override
    public BitcoinNode.DoubleSpendProofAnnouncementHandler createDoubleSpendProofAnnouncementHandler(final BitcoinNode bitcoinNode) {
        return new BitcoinNode.DoubleSpendProofAnnouncementHandler() {
            @Override
            public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> doubleSpendProofsIdentifiers) {
                for (final Sha256Hash doubleSpendProofHash : doubleSpendProofsIdentifiers) {
                    _downloadDoubleSpendProof(doubleSpendProofHash, bitcoinNode);
                }
            }
        };
    }
}
