package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
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
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.secp256k1.Schnorr;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.security.secp256k1.key.PublicKey;
import com.softwareverde.security.secp256k1.signature.Signature;
import com.softwareverde.security.util.HashUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class TransactionSigner {
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
                final Script unlockingScriptForSigning;
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
                        unlockingScriptForSigning = mutableScript;
                    }
                }
                else {
                    unlockingScriptForSigning = UnlockingScript.EMPTY_SCRIPT;
                }

                { // Remove any ByteArrays that should be excluded from the script signing (aka signatures)...
                    final MutableScript modifiedScript = new MutableScript(unlockingScriptForSigning);
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

    protected byte[] _getBitcoinCashBytesForSigning(final SignatureContext signatureContext) {
        final Integer FORK_ID = 0x000000;
        final Transaction transaction = signatureContext.getTransaction();
        final Integer inputIndex = signatureContext.getInputIndexBeingSigned();
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final HashType hashType = signatureContext.getHashType();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // 1. Serialize this Transaction's version...
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(transaction.getVersion()), Endian.LITTLE);
        }

        { // 2. Serialize this Transaction's PreviousTransactionOutputs...
            if (hashType.shouldSignOtherInputs()) {
                final ByteArrayBuilder serializedTransactionInput = new ByteArrayBuilder();
                for (final TransactionInput transactionInput : transactionInputs) {
                    serializedTransactionInput.appendBytes(transactionInput.getPreviousOutputTransactionHash(), Endian.LITTLE);
                    serializedTransactionInput.appendBytes(ByteUtil.integerToBytes(transactionInput.getPreviousOutputIndex()), Endian.LITTLE);
                }

                final byte[] bytes = HashUtil.doubleSha256(serializedTransactionInput.build());
                byteArrayBuilder.appendBytes(bytes);
            }
            else {
                byteArrayBuilder.appendBytes(Sha256Hash.EMPTY_HASH);
            }
        }

        { // 3. Serialize this Transaction's Inputs' SequenceNumbers...
            if ( (! hashType.shouldSignOtherInputs()) || (hashType.getMode() == Mode.SIGNATURE_HASH_NONE) || (hashType.getMode() == Mode.SIGNATURE_HASH_SINGLE) ) {
                byteArrayBuilder.appendBytes(Sha256Hash.EMPTY_HASH);
            }
            else {
                final ByteArrayBuilder serializedSequenceNumbers = new ByteArrayBuilder();
                for (final TransactionInput transactionInput : transactionInputs) {
                    serializedSequenceNumbers.appendBytes(transactionInput.getSequenceNumber().getBytes(), Endian.LITTLE);
                }
                final byte[] bytes = HashUtil.doubleSha256(serializedSequenceNumbers.build());
                byteArrayBuilder.appendBytes(bytes);
            }
        }

        { // 4. Serialize the TransactionInput's PreviousTransactionOutput...
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);

            final ByteArrayBuilder serializedTransactionOutputBeingSpent = new ByteArrayBuilder();
            serializedTransactionOutputBeingSpent.appendBytes(transactionInput.getPreviousOutputTransactionHash(), Endian.LITTLE);
            serializedTransactionOutputBeingSpent.appendBytes(ByteUtil.integerToBytes(transactionInput.getPreviousOutputIndex()), Endian.LITTLE);

            byteArrayBuilder.appendBytes(serializedTransactionOutputBeingSpent.build());
        }

        { // 5. Serialize the script...
            final Script currentScript = signatureContext.getCurrentScript();
            final TransactionOutput transactionOutputBeingSpent = signatureContext.getTransactionOutputBeingSpent(inputIndex);
            final LockingScript outputBeingSpentLockingScript = transactionOutputBeingSpent.getLockingScript();

            final Script scriptForSigning;
            { // Handle Code-Separators...
                final MutableScript mutableScript = new MutableScript(Util.coalesce(currentScript, outputBeingSpentLockingScript));

                final Integer subscriptIndex = signatureContext.getLastCodeSeparatorIndex(inputIndex);
                if (subscriptIndex > 0) {
                    mutableScript.subScript(subscriptIndex);
                }

                mutableScript.removeOperations(Opcode.CODE_SEPARATOR);
                scriptForSigning = mutableScript;
            }

            byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(scriptForSigning.getByteCount()));
            byteArrayBuilder.appendBytes(scriptForSigning.getBytes());
        }

        { // 6. Serialize the amount of the spent TransactionOutput...
            final TransactionOutput transactionOutput = signatureContext.getTransactionOutputBeingSpent(inputIndex);
            byteArrayBuilder.appendBytes(ByteUtil.longToBytes(transactionOutput.getAmount()), Endian.LITTLE);
        }

        { // 7. Serialize the SequenceNumber for this TransactionInput...
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);
            byteArrayBuilder.appendBytes(transactionInput.getSequenceNumber().getBytes(), Endian.LITTLE);
        }

        { // 8. Serialize this Transaction's TransactionOutputs...
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

            if (hashType.getMode() == Mode.SIGNATURE_HASH_SINGLE) {
                if (inputIndex >= transactionOutputs.getCount()) {
                    byteArrayBuilder.appendBytes(Sha256Hash.EMPTY_HASH); // NOTE: This is different behavior than Bitcoin for this error case...
                }
                else {
                    final TransactionOutput transactionOutput = transactionOutputs.get(inputIndex);
                    final LockingScript transactionOutputScript = transactionOutput.getLockingScript();

                    final ByteArrayBuilder serializedTransactionOutput = new ByteArrayBuilder();

                    serializedTransactionOutput.appendBytes(ByteUtil.longToBytes(transactionOutput.getAmount()), Endian.LITTLE);
                    serializedTransactionOutput.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionOutputScript.getByteCount()));
                    serializedTransactionOutput.appendBytes(transactionOutputScript.getBytes());

                    final byte[] bytes = HashUtil.doubleSha256(serializedTransactionOutput.build());
                    byteArrayBuilder.appendBytes(bytes);
                }
            }
            else if (hashType.getMode() == Mode.SIGNATURE_HASH_NONE) {
                byteArrayBuilder.appendBytes(Sha256Hash.EMPTY_HASH);
            }
            else {
                final ByteArrayBuilder serializedTransactionOutput = new ByteArrayBuilder();

                for (final TransactionOutput transactionOutput : transactionOutputs) {
                    final LockingScript transactionOutputScript = transactionOutput.getLockingScript();

                    serializedTransactionOutput.appendBytes(ByteUtil.longToBytes(transactionOutput.getAmount()), Endian.LITTLE);
                    serializedTransactionOutput.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionOutputScript.getByteCount()));
                    serializedTransactionOutput.appendBytes(transactionOutputScript.getBytes());
                }

                final byte[] bytes = HashUtil.doubleSha256(serializedTransactionOutput.build());
                byteArrayBuilder.appendBytes(bytes);
            }
        }

        { // 9. Serialize this Transaction's LockTime...
            final LockTime lockTime = transaction.getLockTime();
            byteArrayBuilder.appendBytes(lockTime.getBytes(), Endian.LITTLE);
        }

        { // 10. Serialize this Transaction's HashType...
            // TODO: Bitcoin ABC has additional code here, including XOR'ing with 0xDEAD... Unsure of its purpose/intent. Might have to revisit.
            final byte hashTypeByte = hashType.toByte();
            final byte[] hashTypeWithForkId = ByteUtil.integerToBytes(FORK_ID << 8);
            hashTypeWithForkId[3] |= hashTypeByte;
            byteArrayBuilder.appendBytes(hashTypeWithForkId, Endian.LITTLE);
        }

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
