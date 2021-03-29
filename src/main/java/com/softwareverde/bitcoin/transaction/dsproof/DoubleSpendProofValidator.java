package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class DoubleSpendProofValidator {
    public static class Context {
        public final UpgradeSchedule upgradeSchedule;
        public final Long headBlockHeight;
        public final MedianBlockTime medianBlockTime;
        public final TransactionOutput transactionOutputBeingSpent;
        public final Transaction conflictingTransaction;

        public Context(final Long headBlockHeight, final MedianBlockTime medianBlockTime, final TransactionOutput transactionOutputBeingSpent, final Transaction conflictingTransaction, final UpgradeSchedule upgradeSchedule) {
            this.upgradeSchedule = upgradeSchedule;
            this.headBlockHeight = headBlockHeight;
            this.medianBlockTime = medianBlockTime;
            this.transactionOutputBeingSpent = transactionOutputBeingSpent;
            this.conflictingTransaction = conflictingTransaction;
        }
    }

    protected final Context _context;

    public DoubleSpendProofValidator(final Context context) {
        _context = context;
    }

    public Boolean isDoubleSpendValid(final DoubleSpendProof doubleSpendProof) {
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
            Logger.trace(doubleSpendProof.getBytes());
            return false;
        }

        // NOTE: This check is disabled since it is performed during storing the proof.
        //  If the lookup wasn't O(N) then the duplicate check wouldn't be a problem.
        //
        // { // Ensure the DoubleSpendProof is unique / is not redundant with an existing DoubleSpendProof...
        //     final DoubleSpendProof redundantDoubleSpendProof = _doubleSpendProofStore.getDoubleSpendProof(transactionOutputIdentifier);
        //     if (redundantDoubleSpendProof != null) { return false; }
        // }

        final DoubleSpendProofPreimageValidator doubleSpendProofPreimageValidator = new DoubleSpendProofPreimageValidator(_context.headBlockHeight, _context.medianBlockTime, _context.upgradeSchedule);

        final Boolean firstProofIsValid = doubleSpendProofPreimageValidator.validateDoubleSpendProof(transactionOutputIdentifier, _context.transactionOutputBeingSpent, _context.conflictingTransaction, doubleSpendProofPreimage0);
        if (! firstProofIsValid) {
            Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; first preimage failed validation.");
            return false;
        }

        final Boolean secondProofIsValid = doubleSpendProofPreimageValidator.validateDoubleSpendProof(transactionOutputIdentifier, _context.transactionOutputBeingSpent, _context.conflictingTransaction, doubleSpendProofPreimage1);
        if (! secondProofIsValid) {
            Logger.trace("DoubleSpendProof " + doubleSpendProofHash + " invalid; second preimage failed validation.");
            return false;
        }

        return true;
    }
}
