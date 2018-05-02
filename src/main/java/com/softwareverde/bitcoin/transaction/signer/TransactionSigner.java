package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.signature.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

public class TransactionSigner {

    // Steps:
    // 1. Set all input-scripts to empty scripts.
    // 2. Set the input's (associated with the inputIndexToBeSigned) unlocking-script to the value of its corresponding output-script from the previous transaction.
    // 3. Append the signatureHashType byte to the serialized transaction bytes.
    // 4. Hash the transaction twice.
    protected byte[] _getBytesForSigning(final SignatureContext signatureContext) {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final HashType hashType = signatureContext.getHashType();
        final HashType.Mode signatureMode = hashType.getMode();

        final Transaction transaction = signatureContext.getTransaction();
        final Script currentScript = signatureContext.getCurrentScript();
        // NOTE: The if the currentScript has not been set, the current script will default to the PreviousTransactionOutput's locking script.
        // if (currentScript == null) { throw new NullPointerException("SignatureContext must have its currentScript set."); }

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(transaction.getVersion());
        mutableTransaction.setHasWitnessData(transaction.hasWitnessData());
        mutableTransaction.setLockTime(transaction.getLockTime());

        for (int inputIndex = 0; inputIndex < transactionInputs.getSize(); ++inputIndex) {
            if (! signatureContext.shouldInputBeSigned(inputIndex)) { continue; }

            final TransactionInput transactionInput = transactionInputs.get(inputIndex);

            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
            mutableTransactionInput.setPreviousOutputIndex(transactionInput.getPreviousOutputIndex());
            mutableTransactionInput.setPreviousOutputTransactionHash(transactionInput.getPreviousOutputTransactionHash());

            if (! signatureContext.shouldInputSequenceNumberBeSigned(inputIndex)) {
                mutableTransactionInput.setSequenceNumber(0L);
            }

            final UnlockingScript unlockingScriptForSigning;
            final Boolean shouldSignScript = signatureContext.shouldInputScriptBeSigned(inputIndex);
            if  (shouldSignScript) {
                final TransactionOutput transactionOutputBeingSpent = signatureContext.getTransactionOutputBeingSpent(inputIndex);
                final LockingScript outputBeingSpentLockingScript = transactionOutputBeingSpent.getLockingScript();

                final Integer subscriptIndex = signatureContext.getLastCodeSeparatorIndex(inputIndex);
                if (subscriptIndex > 0) {
                    final MutableScript mutableScript = new MutableScript(Util.coalesce(currentScript, outputBeingSpentLockingScript));

                    mutableScript.subScript(subscriptIndex);
                    mutableScript.removeOperations(Operation.Opcode.CODE_SEPARATOR);
                    // mutableScript.removeData(scriptSignature); // TODO: Other implementations do this... no one is sure why.
                    unlockingScriptForSigning = UnlockingScript.castFrom(mutableScript);
                }
                else {
                    unlockingScriptForSigning = UnlockingScript.castFrom(Util.coalesce(currentScript, outputBeingSpentLockingScript));
                }
            }
            else {
                unlockingScriptForSigning = UnlockingScript.EMPTY_SCRIPT;
            }
            mutableTransactionInput.setUnlockingScript(unlockingScriptForSigning);
            mutableTransactionInput.setSequenceNumber(transactionInput.getSequenceNumber());
            mutableTransaction.addTransactionInput(mutableTransactionInput);
        }

        for (int outputIndex = 0; outputIndex < transactionOutputs.getSize(); ++outputIndex) {
            if (! signatureContext.shouldOutputBeSigned(outputIndex)) { continue; }

            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
            mutableTransactionOutput.setAmount(transactionOutput.getAmount());
            mutableTransactionOutput.setLockingScript(transactionOutput.getLockingScript());
            mutableTransactionOutput.setIndex(transactionOutput.getIndex());
            mutableTransaction.addTransactionOutput(mutableTransactionOutput);
        }

        final ByteArrayBuilder byteArrayBuilder = transactionDeflater.toByteArrayBuilder(mutableTransaction);
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(ByteUtil.byteToInteger(hashType.toByte())), Endian.LITTLE);
        final byte[] bytes = byteArrayBuilder.build();

        return BitcoinUtil.sha256(BitcoinUtil.sha256(bytes));
    }

    public boolean isSignatureValid(final SignatureContext signatureContext, final PublicKey publicKey, final ScriptSignature scriptSignature) {
        final byte[] bytesForSigning = _getBytesForSigning(signatureContext);
        return Secp256k1.verifySignature(scriptSignature.getSignature(), publicKey, bytesForSigning);
    }

    public Transaction signTransaction(final SignatureContext signatureContext, final PrivateKey privateKey) {
        // NOTE: ensure signatureContext has its lastCodeSeparatorIndex set.

        final MutableTransaction mutableTransaction = new MutableTransaction(signatureContext.getTransaction());
        final byte[] bytesToSign = _getBytesForSigning(signatureContext);
        final Signature signature = Secp256k1.sign(privateKey.getBytes(), bytesToSign);
        final ScriptSignature scriptSignature = new ScriptSignature(signature, signatureContext.getHashType());

        final List<TransactionInput> transactionInputs = mutableTransaction.getTransactionInputs();
        for (int i = 0; i < transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            if (signatureContext.shouldInputScriptBeSigned(i)) {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(transactionInput);
                mutableTransactionInput.setUnlockingScript(ScriptBuilder.unlockPayToAddress(scriptSignature, privateKey.getPublicKey()));
                mutableTransaction.setTransactionInput(i, mutableTransactionInput);
            }
        }

        return mutableTransaction;
    }
}
