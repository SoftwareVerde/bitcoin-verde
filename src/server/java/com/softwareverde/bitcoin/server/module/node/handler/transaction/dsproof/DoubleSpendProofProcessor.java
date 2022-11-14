package com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
import com.softwareverde.bitcoin.context.lazy.DoubleSpendProofUtxoSet;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofValidator;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.UnconfirmedTransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashMap;

public class DoubleSpendProofProcessor {
    public interface Context extends MultiConnectionFullDatabaseContext, MedianBlockTimeContext, UpgradeScheduleContext { }

    protected final Context _context;
    protected final DoubleSpendProofStore _doubleSpendProofStore;

    /**
     * Returns null if the DoubleSpendProofContext was unable to be created (i.e. a required Transaction was not found).
     */
    protected DoubleSpendProofValidator.Context _createDoubleSpendValidatorContext(final DoubleSpendProof doubleSpendProof, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final TransactionOutputIdentifier transactionOutputIdentifier = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();
        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();

        final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        // final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
        final DoubleSpendProofUtxoSet unconfirmedTransactionUtxoSet = new DoubleSpendProofUtxoSet(databaseManager, upgradeSchedule);

        final Long headBlockHeight;
        final MedianBlockTime medianTimePast;
        { // Acquire chain state data for validation...
            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
            if (headBlockHeight == null) { return null; } // Can happen during during fast-sync...

            final Long previousBlockHeight = (headBlockHeight - 1L);
            medianTimePast = _context.getMedianBlockTime(previousBlockHeight);
        }

        final Transaction firstSeenTransaction;
        { // Load the conflicting Transaction from the mempool...
            final UnconfirmedTransactionInputDatabaseManager transactionInputDatabaseManager = databaseManager.getUnconfirmedTransactionInputDatabaseManager();
            final UnconfirmedTransactionInputId unconfirmedTransactionInputId = transactionInputDatabaseManager.getUnconfirmedTransactionInputIdSpendingTransactionOutput(transactionOutputIdentifier);
            if (unconfirmedTransactionInputId == null) {
                Logger.debug("DoubleSpendProof " + doubleSpendProofHash + " invalid; conflicting transaction input spending output not found: " + transactionOutputIdentifier);
                return null;
            }

            final TransactionId transactionId = transactionInputDatabaseManager.getTransactionId(unconfirmedTransactionInputId);
            firstSeenTransaction = transactionDatabaseManager.getTransaction(transactionId);
            if (firstSeenTransaction == null) {
                Logger.debug("DoubleSpendProof " + doubleSpendProofHash + " invalid; transaction not found.");
                return null;
            }
        }

        final HashMap<TransactionOutputIdentifier, TransactionOutput> previousTransactionOutputs = new HashMap<>();
        for (final TransactionInput transactionInput : firstSeenTransaction.getTransactionInputs()) {
            final TransactionOutputIdentifier previousOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutput previousTransactionOutput = unconfirmedTransactionUtxoSet.getTransactionOutput(previousOutputIdentifier);
            previousTransactionOutputs.put(previousOutputIdentifier, previousTransactionOutput); // NOTE: may be null.
        }

        return new DoubleSpendProofValidator.Context(headBlockHeight, medianTimePast, previousTransactionOutputs, firstSeenTransaction, upgradeSchedule);
    }

    public DoubleSpendProofProcessor(final DoubleSpendProofStore doubleSpendProofStore, final Context context) {
        _doubleSpendProofStore = doubleSpendProofStore;
        _context = context;
    }

    public Boolean processDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        final DoubleSpendProofValidator.Context doubleSpendValidatorContext;
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            doubleSpendValidatorContext = _createDoubleSpendValidatorContext(doubleSpendProof, databaseManager);
        }
        catch (final DatabaseException exception) {
            final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
            _doubleSpendProofStore.banDoubleSpendProof(doubleSpendProofHash);
            Logger.debug("Unable to create DoubleSpendProof context.", exception);
            return false;
        }

        if (doubleSpendValidatorContext == null) {
            _doubleSpendProofStore.storePendingDoubleSpendProof(doubleSpendProof);
            return false;
        }

        final DoubleSpendProofValidator doubleSpendProofValidator = new DoubleSpendProofValidator(doubleSpendValidatorContext);
        final Boolean doubleSpendProofIsValid = doubleSpendProofValidator.isDoubleSpendValid(doubleSpendProof);
        if (! doubleSpendProofIsValid) {
            final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
            _doubleSpendProofStore.banDoubleSpendProof(doubleSpendProofHash);
            Logger.debug("Invalid DoubleSpendProof: " + doubleSpendProofHash);
            return false;
        }

        final Boolean wasUnseen = _doubleSpendProofStore.storeDoubleSpendProof(doubleSpendProof);
        Logger.debug("DoubleSpendProof Accepted: " + wasUnseen);
        return wasUnseen;
    }
}
