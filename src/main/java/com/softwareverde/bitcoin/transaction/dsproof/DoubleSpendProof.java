package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimageDeflater;
import com.softwareverde.bitcoin.server.message.type.dsproof.MutableDoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignatureContext;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.BitcoinCashTransactionSignerUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class DoubleSpendProof implements Hashable, Const {
    public static List<HashType> SUPPORTED_HASH_TYPES = new ImmutableList<HashType>(
        new HashType(Mode.SIGNATURE_HASH_ALL, true, false),     // HashType: 0x01
        new HashType(Mode.SIGNATURE_HASH_SINGLE, true, false)   // HashType: 0x03
    );

    public static Boolean arePreimagesInCanonicalOrder(final DoubleSpendProofPreimage doubleSpendProofPreimage0, final DoubleSpendProofPreimage doubleSpendProofPreimage1) {
        final DoubleSpendProofPreimageDeflater doubleSpendProofPreimageDeflater = new DoubleSpendProofPreimageDeflater();

        {
            final Sha256Hash transactionOutputsDigest0 = doubleSpendProofPreimage0.getExecutedTransactionOutputsDigest().toReversedEndian();
            final Sha256Hash transactionOutputsDigest1 = doubleSpendProofPreimage1.getExecutedTransactionOutputsDigest().toReversedEndian();
            final int compareValue = transactionOutputsDigest0.compareTo(transactionOutputsDigest1);
            if (compareValue > 0) { return false; }
            else if (compareValue < 0) { return true; }
        }

        {
            final Sha256Hash previousOutputsDigest0 = doubleSpendProofPreimage0.getPreviousOutputsDigest().toReversedEndian();
            final Sha256Hash previousOutputsDigest1 = doubleSpendProofPreimage1.getPreviousOutputsDigest().toReversedEndian();
            final int compareValue = previousOutputsDigest0.compareTo(previousOutputsDigest1);
            if (compareValue > 0) { return false; }
            else if (compareValue < 0) { return true; }
        }

        {
            final ByteArray previousOutputsDigest0Extra = doubleSpendProofPreimageDeflater.serializeExtraTransactionOutputDigestsForSorting(doubleSpendProofPreimage0);
            final ByteArray previousOutputsDigest1Extra = doubleSpendProofPreimageDeflater.serializeExtraTransactionOutputDigestsForSorting(doubleSpendProofPreimage1);
            final int extraPreviousOutputDigestLexCompare = ByteUtil.compareByteArrayLexicographically(previousOutputsDigest0Extra, previousOutputsDigest1Extra);
            if (extraPreviousOutputDigestLexCompare > 0) { return false; }
        }

        return true;
    }

    /**
     * Returns the index of the transactionInput that spends the provided previousOutputIdentifier.
     */
    protected static Integer getTransactionInput(final TransactionOutputIdentifier previousOutputIdentifier, final Transaction transaction) {
        int inputIndex = 0;
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (final TransactionInput transactionInput : transactionInputs) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            if (Util.areEqual(previousOutputIdentifier, transactionOutputIdentifier)) {
                return inputIndex;
            }

            inputIndex += 1;
        }
        return null;
    }

    protected static DoubleSpendProofPreimage createDoubleSpendProofPreimage(final Transaction transaction, final TransactionOutputIdentifier previousOutputIdentifier, final ScriptType previousOutputScriptType) {
        final Integer transactionInputIndex = DoubleSpendProof.getTransactionInput(previousOutputIdentifier, transaction);
        if (transactionInputIndex == null) { return null; }

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final TransactionInput transactionInput = transactionInputs.get(transactionInputIndex);

        final HashType payToPublicKeyHashHashType;
        {
            if (previousOutputScriptType == ScriptType.PAY_TO_PUBLIC_KEY_HASH) {
                final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
                if (unlockingScript.containsNonPushOperations()) { return null; }

                final List<Operation> operations = unlockingScript.getOperations();
                if (operations.isEmpty()) { return null; }

                final PushOperation signaturePushOperation = (PushOperation) operations.get(0);
                final Value pushedSignatureValue = signaturePushOperation.getValue();
                final ScriptSignature scriptSignature = pushedSignatureValue.asScriptSignature(ScriptSignatureContext.CHECK_SIGNATURE);
                if (scriptSignature == null) { return null; }

                payToPublicKeyHashHashType = scriptSignature.getHashType();
            }
            else {
                payToPublicKeyHashHashType = null;
            }
        }

        // P2PKH DoubleSpendProofs always relay the actual preimage used during signature execution.
        final boolean shouldOverrideHashTypeToPayToPublicKeyHash = (payToPublicKeyHashHashType != null);

        final MutableDoubleSpendProofPreimage doubleSpendProofPreimage = new MutableDoubleSpendProofPreimage();

        // #1
        final Long transactionVersion = transaction.getVersion();
        doubleSpendProofPreimage.setTransactionVersion(transactionVersion);

        { // #2
            final HashType hashType;
            if (shouldOverrideHashTypeToPayToPublicKeyHash) {
                hashType = payToPublicKeyHashHashType;
            }
            else {
                // For non-P2PKH scripts, the DSProof always includes the more complicated digest even if it is
                //  overwritten during signature validation by an empty hash.
                hashType = new HashType(Mode.SIGNATURE_HASH_ALL, true, true);
            }

            final Sha256Hash previousOutputsDigest = BitcoinCashTransactionSignerUtil.getPreviousOutputIdentifiersHash(transaction, hashType);
            doubleSpendProofPreimage.setPreviousOutputsDigest(previousOutputsDigest);
        }

        { // #3
            final HashType hashType;
            if (shouldOverrideHashTypeToPayToPublicKeyHash) {
                hashType = payToPublicKeyHashHashType;
            }
            else {
                // HashType Modes of SINGLE/ANYONECANPAY/NONE result in the preimage using an empty Sha256Hash, and non-P2PKH
                //  DoubleSpendProofs always relay the more complicated digest, even if it is not used.
                hashType = new HashType(Mode.SIGNATURE_HASH_ALL, true, true);
            }
            final Sha256Hash sequenceNumbersDigest = BitcoinCashTransactionSignerUtil.getTransactionInputsSequenceNumbersHash(transaction, hashType);
            doubleSpendProofPreimage.setSequenceNumbersDigest(sequenceNumbersDigest);
        }

        // #4 is the previousOutputIdentifier
        // #5 and #6 are not provided by the DoubleSpendProof

        // #7
        final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
        doubleSpendProofPreimage.setSequenceNumber(sequenceNumber);

        { // #8
            if (shouldOverrideHashTypeToPayToPublicKeyHash) {
                final Sha256Hash transactionOutputsDigest = BitcoinCashTransactionSignerUtil.getTransactionOutputsHash(transaction, transactionInputIndex, payToPublicKeyHashHashType);
                doubleSpendProofPreimage.setExecutedTransactionOutputsDigest(transactionOutputsDigest);
            }
            else {
                final Sha256Hash transactionOutputsDigest = Sha256Hash.EMPTY_HASH; // TODO: Change in version 2 of DSProof.
                doubleSpendProofPreimage.setExecutedTransactionOutputsDigest(transactionOutputsDigest);

                // All supported HashTypes should always be included in order to provide a canonical form/identifier for the DSProof when extras are provided.
                for (final HashType hashType : DoubleSpendProof.SUPPORTED_HASH_TYPES) {
                    final Sha256Hash transactionOutputsDigestSignatureHashSingle = BitcoinCashTransactionSignerUtil.getTransactionOutputsHash(transaction, transactionInputIndex, hashType);
                    doubleSpendProofPreimage.setTransactionOutputsDigest(hashType, transactionOutputsDigestSignatureHashSingle);
                }
            }
        }

        // #9
        final LockTime lockTime = transaction.getLockTime();
        doubleSpendProofPreimage.setLockTime(lockTime);

        { // # PushData
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
            if (unlockingScript.containsNonPushOperations()) { return null; }

            final List<Operation> operations = unlockingScript.getOperations();
            final int operationCount = operations.getCount();

            for (int i = 0; i < operationCount; ++i) {
                if ( (previousOutputScriptType == ScriptType.PAY_TO_PUBLIC_KEY_HASH) || (previousOutputScriptType == ScriptType.PAY_TO_SCRIPT_HASH) ) {
                    if (i == operationCount - 1) { break; }
                }

                final PushOperation pushOperation = (PushOperation) operations.get(i);
                final Value pushOperationValue = pushOperation.getValue();
                doubleSpendProofPreimage.addUnlockingScriptPushData(pushOperationValue);
            }
        }

        return doubleSpendProofPreimage;
    }

    public static DoubleSpendProofWithTransactions createDoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent, final ScriptType previousOutputScriptType, final Transaction firstSeenTransaction, final Transaction doubleSpendTransaction) {
        final String debugIdentifier = ((firstSeenTransaction != null ? firstSeenTransaction.getHash() : "null") + " " + (doubleSpendTransaction != null ? doubleSpendTransaction.getHash() : "null"));

        if ( (firstSeenTransaction == null) || (doubleSpendTransaction == null) ) {
            Logger.debug("Unable to create DoubleSpendProof: " + debugIdentifier);
            return null;
        }

        return DoubleSpendProofWithTransactions.create(firstSeenTransaction, doubleSpendTransaction, transactionOutputIdentifierBeingSpent, previousOutputScriptType);
    }

    protected final TransactionOutputIdentifier _transactionOutputIdentifierBeingDoubleSpent;
    protected final DoubleSpendProofPreimage _doubleSpendProofPreimage0;
    protected final DoubleSpendProofPreimage _doubleSpendProofPreimage1;

    protected ByteArray _cachedBytes = null;
    protected Sha256Hash _cachedHash = null;

    protected ByteArray _getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // Serialize the previous output identifier...
            final Sha256Hash previousTransactionOutputHash = _transactionOutputIdentifierBeingDoubleSpent.getTransactionHash();
            final Integer previousTransactionOutputIndex = _transactionOutputIdentifierBeingDoubleSpent.getOutputIndex();
            byteArrayBuilder.appendBytes(previousTransactionOutputHash, Endian.LITTLE);
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(previousTransactionOutputIndex), Endian.LITTLE);
        }

        { // Serialize the double-spend-proofs...
            final DoubleSpendProofPreimageDeflater doubleSpendProofPreimageDeflater = new DoubleSpendProofPreimageDeflater();
            final ByteArray doubleSpendProofDigestBytes0 = doubleSpendProofPreimageDeflater.toBytes(_doubleSpendProofPreimage0);
            final ByteArray doubleSpendProofDigestBytes1 = doubleSpendProofPreimageDeflater.toBytes(_doubleSpendProofPreimage1);
            byteArrayBuilder.appendBytes(doubleSpendProofDigestBytes0, Endian.BIG);
            byteArrayBuilder.appendBytes(doubleSpendProofDigestBytes1, Endian.BIG);
        }

        { // Serialize extra TransactionOutputDigests, if there are any...
            final List<HashType> preimage0HashTypes = _doubleSpendProofPreimage0.getExtraTransactionOutputsDigestHashTypes();
            final List<HashType> preimage1HashTypes = _doubleSpendProofPreimage1.getExtraTransactionOutputsDigestHashTypes();

            final int preimage0HashTypesCount = preimage0HashTypes.getCount();
            final int preimage1HashTypesCount = preimage1HashTypes.getCount();
            if ( (preimage0HashTypesCount > 0) || (preimage1HashTypesCount > 0) ) {
                final DoubleSpendProofPreimageDeflater doubleSpendProofPreimageDeflater = new DoubleSpendProofPreimageDeflater();
                final ByteArray extraDigestsBytes0 = doubleSpendProofPreimageDeflater.serializeExtraTransactionOutputDigests(_doubleSpendProofPreimage0);
                final ByteArray extraDigestsBytes1 = doubleSpendProofPreimageDeflater.serializeExtraTransactionOutputDigests(_doubleSpendProofPreimage1);

                byteArrayBuilder.appendBytes(extraDigestsBytes0);
                byteArrayBuilder.appendBytes(extraDigestsBytes1);
            }
        }

        return byteArrayBuilder;
    }

    public DoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifier, final DoubleSpendProofPreimage doubleSpendProofPreimage0, final DoubleSpendProofPreimage doubleSpendProofPreimage1) {
        _transactionOutputIdentifierBeingDoubleSpent = transactionOutputIdentifier;
        _doubleSpendProofPreimage0 = doubleSpendProofPreimage0;
        _doubleSpendProofPreimage1 = doubleSpendProofPreimage1;
    }

    public TransactionOutputIdentifier getTransactionOutputIdentifierBeingDoubleSpent() {
        return _transactionOutputIdentifierBeingDoubleSpent;
    }

    public DoubleSpendProofPreimage getDoubleSpendProofPreimage0() {
        return _doubleSpendProofPreimage0;
    }

    public DoubleSpendProofPreimage getDoubleSpendProofPreimage1() {
        return _doubleSpendProofPreimage1;
    }

    public ByteArray getBytes() {
        if (_cachedBytes == null) {
            _cachedBytes = _getBytes();
        }

        return _cachedBytes;
    }

    @Override
    public Sha256Hash getHash() {
        if (_cachedHash == null) {
            if (_cachedBytes == null) {
                _cachedBytes = _getBytes();
            }

            _cachedHash = HashUtil.doubleSha256(_cachedBytes).toReversedEndian();
        }

        return _cachedHash;
    }
}
