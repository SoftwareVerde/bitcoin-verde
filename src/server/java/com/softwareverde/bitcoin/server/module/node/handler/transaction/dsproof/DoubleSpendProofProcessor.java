package com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimageDeflater;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofValidator;
import com.softwareverde.bitcoin.transaction.input.UnconfirmedTransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class DoubleSpendProofProcessor {
    public interface Context extends MultiConnectionFullDatabaseContext, MedianBlockTimeContext, UpgradeScheduleContext { }

    protected final Context _context;
    protected final DoubleSpendProofStore _doubleSpendProofStore;

    /**
     * Returns null IFF the DoubleSpendProof was unable to be determined valid or invalid (i.e. a required Transaction was not found).
     * Returns true IFF the DoubleSpendProof was able to be validated and was valid.
     * Returns false IFF the DoubleSpendProof was able to be validated and was invalid.
     */
    protected Boolean _isDoubleSpendValid(final DoubleSpendProof doubleSpendProof) {
        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
        final TransactionOutputIdentifier transactionOutputIdentifier = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();

        final DoubleSpendProofPreimage doubleSpendProofPreimage0 = doubleSpendProof.getDoubleSpendProofPreimage0();
        final DoubleSpendProofPreimage doubleSpendProofPreimage1 = doubleSpendProof.getDoubleSpendProofPreimage1();

        { // Ensure preimages are unique...
            final List<ByteArray> unlockingScriptData0 = doubleSpendProofPreimage0.getUnlockingScriptPushData();
            final List<ByteArray> unlockingScriptData1 = doubleSpendProofPreimage1.getUnlockingScriptPushData();
            if (Util.areEqual(unlockingScriptData0, unlockingScriptData1)) {
                // TODO: v2 should allow duplicate/non-existent second proof for anyone-can-spend/SIGNATURE_HASH_NONE.
                Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; duplicate preimage.");
                return false;
            }
        }

        final Boolean preimagesAreInCanonicalOrder = DoubleSpendProof.arePreimagesInCanonicalOrder(doubleSpendProofPreimage0, doubleSpendProofPreimage1);
        if (! preimagesAreInCanonicalOrder) {
            Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; incorrect preimage order.");
            return false;
        }

        // NOTE: This check is disabled since it is performed during storing the proof.
        //  If the lookup wasn't O(N) then the duplicate check wouldn't be a problem.
        //
        // { // Ensure the DoubleSpendProof is unique / is not redundant with an existing DoubleSpendProof...
        //     final DoubleSpendProof redundantDoubleSpendProof = _doubleSpendProofStore.getDoubleSpendProof(transactionOutputIdentifier);
        //     if (redundantDoubleSpendProof != null) { return false; }
        // }

        final Long headBlockHeight;
        final MedianBlockTime medianBlockTime;
        final Transaction transactionBeingSpent;
        final Transaction conflictingTransaction;
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            { // Acquire chainstate data for validation...
                final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);

                final Long previousBlockHeight = (headBlockHeight - 1L);
                medianBlockTime = _context.getMedianBlockTime(previousBlockHeight);
            }

            { // Load the Transaction being spent from the database...
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                if (transactionId == null) {
                    Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; transaction not found: " + transactionHash);
                    return null;
                }

                transactionBeingSpent = transactionDatabaseManager.getTransaction(transactionId);
            }

            { // Load the conflicting Transaction from the mempool...
                final UnconfirmedTransactionInputDatabaseManager transactionInputDatabaseManager = databaseManager.getUnconfirmedTransactionInputDatabaseManager();
                final UnconfirmedTransactionInputId unconfirmedTransactionInputId = transactionInputDatabaseManager.getUnconfirmedTransactionInputIdSpendingTransactionOutput(transactionOutputIdentifier);
                if (unconfirmedTransactionInputId == null) {
                    Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; conflicting transaction input spending output not found: " + transactionOutputIdentifier);
                    return null;
                }

                final TransactionId transactionId = transactionInputDatabaseManager.getTransactionId(unconfirmedTransactionInputId);
                conflictingTransaction = transactionDatabaseManager.getTransaction(transactionId);
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return false;
        }

        if ( (transactionBeingSpent == null) || (conflictingTransaction == null) ) {
            Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; dependent transaction not found spending: " + transactionOutputIdentifier);
            return null;
        }

        final TransactionOutput transactionOutputBeingSpent;
        {
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
            final List<TransactionOutput> transactionOutputs = transactionBeingSpent.getTransactionOutputs();
            if (transactionOutputIndex >= transactionOutputs.getCount()) {
                Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; transaction spends out-of-bounds output.");
                return false;
            }

            transactionOutputBeingSpent = transactionOutputs.get(transactionOutputIndex);
        }

        final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
        final DoubleSpendProofValidator doubleSpendProofValidator = new DoubleSpendProofValidator(headBlockHeight, medianBlockTime, upgradeSchedule);

        final Boolean firstProofIsValid = doubleSpendProofValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutputBeingSpent, conflictingTransaction, doubleSpendProofPreimage0);
        if (! firstProofIsValid) {
            Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; first preimage failed validation.");
            return false;
        }

        final Boolean secondProofIsValid = doubleSpendProofValidator.validateDoubleSpendProof(transactionOutputIdentifier, transactionOutputBeingSpent, conflictingTransaction, doubleSpendProofPreimage1);
        if (! secondProofIsValid) {
            Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; second preimage failed validation.");
            return false;
        }

        return true;
    }

    public DoubleSpendProofProcessor(final DoubleSpendProofStore doubleSpendProofStore, final Context context) {
        _doubleSpendProofStore = doubleSpendProofStore;
        _context = context;
    }

    public Boolean processDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        final Boolean doubleSpendProofIsValid = _isDoubleSpendValid(doubleSpendProof);
        if (doubleSpendProofIsValid == null) {
            _doubleSpendProofStore.storePendingDoubleSpendProof(doubleSpendProof);
            return false;
        }
        else if (! doubleSpendProofIsValid) {
            final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
            _doubleSpendProofStore.banDoubleSpendProof(doubleSpendProofHash);
            return false;
        }

        final Boolean wasUnseen = _doubleSpendProofStore.storeDoubleSpendProof(doubleSpendProof);
        return wasUnseen;
    }
}
