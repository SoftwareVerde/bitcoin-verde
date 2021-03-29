package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.secp256k1.Schnorr;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.secp256k1.signature.Signature;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class TransactionSigner {
    public static class BitcoinCashSignaturePreimage {
        public ByteArray transactionVersionBytes;           // Step 1,  LE
        public ByteArray previousTransactionOutputsDigest;  // Step 2,  BE
        public ByteArray sequenceNumbersDigest;             // Step 3,  BE
        public ByteArray previousOutputBytes;               // Step 4,  BE
        public ByteArray scriptBytes;                       // Step 5,  BE
        public ByteArray transactionOutputAmountBytes;      // Step 6,  LE
        public ByteArray sequenceNumberBytes;               // Step 7,  LE
        public ByteArray transactionOutputsDigest;          // Step 8,  BE
        public ByteArray lockTimeBytes;                     // Step 9,  LE
        public ByteArray hashTypeBytes;                     // Step 10, LE
    }

    private static final byte[] INVALID_SIGNATURE_HASH_SINGLE_VALUE = HexUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000");

    protected byte[] _getBytesForSigning(final SignatureContext signatureContext) {
        if (! signatureContext.shouldUseBitcoinCashSigningAlgorithm()) {
            return _getBitcoinCoreBytesForSigning(signatureContext);
        }

        return _getBitcoinCashBytesForSigning(signatureContext);
    }

    // Steps:
    // 1. Set all input-scripts to empty scripts.
    // 2. Set the input's (associated with the inputIndexToBeSigned) unlocking-script to the value of its corresponding output-script from the previous transaction.
    // 3. Append the signatureHashType byte to the serialized transaction bytes.
    // 4. Hash the transaction twice.
    protected byte[] _getBitcoinCoreBytesForSigning(final SignatureContext signatureContext) {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final Transaction transaction = signatureContext.getTransaction();
        // NOTE: The if the currentScript has not been set, the current script will default to the PreviousTransactionOutput's locking script.
        // if (currentScript == null) { throw new NullPointerException("SignatureContext must have its currentScript set."); }

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final HashType hashType = signatureContext.getHashType();

        { // Bitcoin Core Bug: https://bitcointalk.org/index.php?topic=260595.0
            // This bug is caused when an input uses SigHash Single without a matching output.
            // Originally, the Bitcoin Core client returned "1" as the bytes to be hashed, but the invoker never checked
            // for that case, which caused the "1" value to be the actual bytes that are signed for the whole transaction.
            final Mode signatureMode = hashType.getMode();
            if (signatureMode == Mode.SIGNATURE_HASH_SINGLE) {
                if (signatureContext.getInputIndexBeingSigned() >= transactionOutputs.getCount()) {
                    return INVALID_SIGNATURE_HASH_SINGLE_VALUE;
                }
            }
        }

        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(transaction.getVersion());
        mutableTransaction.setLockTime(transaction.getLockTime());

        for (int inputIndex = 0; inputIndex < transactionInputs.getCount(); ++inputIndex) {
            if (! signatureContext.shouldInputBeSigned(inputIndex)) { continue; }

            final TransactionInput transactionInput = transactionInputs.get(inputIndex);

            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
            mutableTransactionInput.setPreviousOutputIndex(transactionInput.getPreviousOutputIndex());
            mutableTransactionInput.setPreviousOutputTransactionHash(transactionInput.getPreviousOutputTransactionHash());

            { // Handle Input-Script Signing...
                final Script surrogateUnlockingScriptForSigning;
                final Boolean shouldSignScript = signatureContext.shouldInputScriptBeSigned(inputIndex);
                if (shouldSignScript) {
                    final Script currentScript = signatureContext.getCurrentScript();
                    final TransactionOutput transactionOutputBeingSpent = signatureContext.getTransactionOutputBeingSpent(inputIndex);
                    final LockingScript outputBeingSpentLockingScript = transactionOutputBeingSpent.getLockingScript();

                    { // Handle Code-Separators...
                        final MutableScript mutableScript = new MutableScript(Util.coalesce(currentScript, outputBeingSpentLockingScript));

                        final Integer subscriptIndex = signatureContext.getLastCodeSeparatorIndex(inputIndex);
                        if (subscriptIndex > 0) {
                            mutableScript.subScript(subscriptIndex);
                        }

                        mutableScript.removeOperations(Opcode.CODE_SEPARATOR);
                        surrogateUnlockingScriptForSigning = mutableScript;
                    }
                }
                else {
                    surrogateUnlockingScriptForSigning = UnlockingScript.EMPTY_SCRIPT;
                }

                { // Remove any ByteArrays that should be excluded from the script signing (aka signatures)...
                    final MutableScript modifiedScript = new MutableScript(surrogateUnlockingScriptForSigning);
                    final List<ByteArray> bytesToExcludeFromScript = signatureContext.getBytesToExcludeFromScript();
                    for (final ByteArray byteArray : bytesToExcludeFromScript) {
                        modifiedScript.removePushOperations(byteArray);
                    }
                    mutableTransactionInput.setUnlockingScript(UnlockingScript.castFrom(modifiedScript));
                }
            }

            { // Handle Input-Sequence-Number Signing...
                if (signatureContext.shouldInputSequenceNumberBeSigned(inputIndex)) {
                    mutableTransactionInput.setSequenceNumber(transactionInput.getSequenceNumber());
                }
                else {
                    mutableTransactionInput.setSequenceNumber(SequenceNumber.EMPTY_SEQUENCE_NUMBER);
                }
            }

            mutableTransaction.addTransactionInput(mutableTransactionInput);
        }

        for (int outputIndex = 0; outputIndex < transactionOutputs.getCount(); ++outputIndex) {
            if (! signatureContext.shouldOutputBeSigned(outputIndex)) { continue; } // If the output should not be signed, then it is omitted from the signature completely...

            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();

            { // Handle Output-Amounts Signing...
                if (signatureContext.shouldOutputAmountBeSigned(outputIndex)) {
                    mutableTransactionOutput.setAmount(transactionOutput.getAmount());
                }
                else {
                    mutableTransactionOutput.setAmount(-1L);
                }
            }

            { // Handle Output-Script Signing...
                if (signatureContext.shouldOutputScriptBeSigned(outputIndex)) {
                    mutableTransactionOutput.setLockingScript(transactionOutput.getLockingScript());
                }
                else {
                    mutableTransactionOutput.setLockingScript(LockingScript.EMPTY_SCRIPT);
                }
            }

            mutableTransactionOutput.setIndex(transactionOutput.getIndex());
            mutableTransaction.addTransactionOutput(mutableTransactionOutput);
        }

        final ByteArrayBuilder byteArrayBuilder = transactionDeflater.toByteArrayBuilder(mutableTransaction);
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(ByteUtil.byteToInteger(hashType.toByte())), Endian.LITTLE);
        final byte[] bytes = byteArrayBuilder.build();
        return HashUtil.doubleSha256(bytes);
    }

    protected BitcoinCashSignaturePreimage _getBitcoinCashPreimage(final SignatureContext signatureContext) {
        final Transaction transaction = signatureContext.getTransaction();
        final Integer transactionInputIndex = signatureContext.getInputIndexBeingSigned();
        final HashType hashType = signatureContext.getHashType();

        final BitcoinCashSignaturePreimage signaturePreimage = new BitcoinCashSignaturePreimage();

        { // 1. Serialize this Transaction's version...
            signaturePreimage.transactionVersionBytes = BitcoinCashTransactionSignerUtil.getTransactionVersionBytes(transaction);
        }

        { // 2. Serialize this Transaction's TransactionInputs' previous TransactionOutputIdentifiers...
            signaturePreimage.previousTransactionOutputsDigest = BitcoinCashTransactionSignerUtil.getPreviousOutputIdentifiersHash(transaction, hashType);
        }

        { // 3. Serialize this Transaction's Inputs' SequenceNumbers...
            signaturePreimage.sequenceNumbersDigest = BitcoinCashTransactionSignerUtil.getTransactionInputsSequenceNumbersHash(transaction, hashType);
        }

        { // 4. Serialize the signed TransactionInput's previous TransactionOutputIdentifier...
            signaturePreimage.previousOutputBytes = BitcoinCashTransactionSignerUtil.getPreviousTransactionOutputIdentifierBytes(transaction, transactionInputIndex);
        }

        { // 5. Serialize the script...
            final Script currentScript = signatureContext.getCurrentScript();
            final TransactionOutput transactionOutputBeingSpent = signatureContext.getTransactionOutputBeingSpent(transactionInputIndex);
            final LockingScript outputBeingSpentLockingScript = transactionOutputBeingSpent.getLockingScript();

            final Script scriptForSigning;
            { // Handle Code-Separators...
                final MutableScript mutableScript = new MutableScript(Util.coalesce(currentScript, outputBeingSpentLockingScript));

                final Integer subscriptIndex = signatureContext.getLastCodeSeparatorIndex(transactionInputIndex);
                if (subscriptIndex > 0) {
                    mutableScript.subScript(subscriptIndex);
                }

                // NOTE: (Subtly) According to Buip55 CODE_SEPARATOR are not removed in the new BCH serialization format.
                // mutableScript.removeOperations(Opcode.CODE_SEPARATOR);

                scriptForSigning = mutableScript;
            }

            final int scriptByteCount = scriptForSigning.getByteCount();
            final ByteArray scriptByteCountBytes = ByteArray.wrap(ByteUtil.variableLengthIntegerToBytes(scriptByteCount));
            final ByteArray scriptBytes = scriptForSigning.getBytes();

            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            byteArrayBuilder.appendBytes(scriptByteCountBytes);
            byteArrayBuilder.appendBytes(scriptBytes);
            signaturePreimage.scriptBytes = byteArrayBuilder;
        }

        { // 6. Serialize the amount of the spent TransactionOutput...
            final TransactionOutput transactionOutput = signatureContext.getTransactionOutputBeingSpent(transactionInputIndex);
            signaturePreimage.transactionOutputAmountBytes = BitcoinCashTransactionSignerUtil.getPreviousTransactionOutputAmountBytes(transactionOutput);
        }

        { // 7. Serialize the SequenceNumber for this TransactionInput...
            signaturePreimage.sequenceNumberBytes = BitcoinCashTransactionSignerUtil.getSequenceNumberBytes(transaction, transactionInputIndex);
        }

        { // 8. Serialize this Transaction's TransactionOutputs...
            signaturePreimage.transactionOutputsDigest = BitcoinCashTransactionSignerUtil.getTransactionOutputsHash(transaction, transactionInputIndex, hashType);
        }

        { // 9. Serialize this Transaction's LockTime...
            signaturePreimage.lockTimeBytes = BitcoinCashTransactionSignerUtil.getTransactionLockTimeBytes(transaction);
        }

        { // 10. Serialize this Transaction's HashType...
            signaturePreimage.hashTypeBytes = BitcoinCashTransactionSignerUtil.getTransactionHashTypeBytes(hashType);
        }

        return signaturePreimage;
    }

    protected byte[] _getBitcoinCashBytesForSigning(final SignatureContext signatureContext) {
        final BitcoinCashSignaturePreimage bitcoinCashSignaturePreimage = _getBitcoinCashPreimage(signatureContext);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.transactionVersionBytes);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.previousTransactionOutputsDigest);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.sequenceNumbersDigest);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.previousOutputBytes);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.scriptBytes);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.transactionOutputAmountBytes);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.sequenceNumberBytes);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.transactionOutputsDigest);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.lockTimeBytes);
        byteArrayBuilder.appendBytes(bitcoinCashSignaturePreimage.hashTypeBytes);

        return HashUtil.doubleSha256(byteArrayBuilder.build());
    }

    protected Transaction _signTransaction(final SignatureContext signatureContext, final PrivateKey privateKey, final Boolean useCompressedPublicKey) {
        // NOTE: ensure signatureContext has its lastCodeSeparatorIndex set.

        final PublicKey publicKey;
        {
            final PublicKey uncompressedPublicKey = privateKey.getPublicKey();
            if (useCompressedPublicKey) {
                publicKey = uncompressedPublicKey.compress();
            }
            else {
                publicKey = uncompressedPublicKey.decompress();
            }
        }

        final MutableTransaction mutableTransaction = new MutableTransaction(signatureContext.getTransaction());
        final byte[] bytesToSign = _getBytesForSigning(signatureContext);
        final Signature signature = Secp256k1.sign(privateKey, bytesToSign);
        final ScriptSignature scriptSignature = new ScriptSignature(signature, signatureContext.getHashType());

        final List<TransactionInput> transactionInputs = mutableTransaction.getTransactionInputs();
        for (int i = 0; i < transactionInputs.getCount(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            if (signatureContext.shouldInputScriptBeSigned(i)) {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(transactionInput);
                mutableTransactionInput.setUnlockingScript(ScriptBuilder.unlockPayToAddress(scriptSignature, publicKey));
                mutableTransaction.setTransactionInput(i, mutableTransactionInput);
            }
        }

        return mutableTransaction;
    }

    public boolean isSignatureValid(final SignatureContext signatureContext, final PublicKey publicKey, final ScriptSignature scriptSignature) {
        final byte[] bytesForSigning = _getBytesForSigning(signatureContext);

        final Signature signature = scriptSignature.getSignature();
        if (signature.getType() == Signature.Type.SCHNORR) {
            return Schnorr.verifySignature(signature, publicKey, bytesForSigning);
        }
        else {
            return Secp256k1.verifySignature(signature, publicKey, bytesForSigning);
        }
    }

    public ScriptSignature createSignature(final SignatureContext signatureContext, final PrivateKey privateKey) {
        final byte[] bytesToSign = _getBytesForSigning(signatureContext);
        final Signature signature = Secp256k1.sign(privateKey, bytesToSign);
        return new ScriptSignature(signature, signatureContext.getHashType());
    }

    public Transaction signTransaction(final SignatureContext signatureContext, final PrivateKey privateKey) {
        return _signTransaction(signatureContext, privateKey, false);
    }

    public Transaction signTransaction(final SignatureContext signatureContext, final PrivateKey privateKey, final Boolean useCompressedPublicKey) {
        return _signTransaction(signatureContext, privateKey, useCompressedPublicKey);
    }
}
