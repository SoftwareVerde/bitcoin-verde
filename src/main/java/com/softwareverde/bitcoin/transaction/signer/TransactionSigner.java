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
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptDeflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;

public class TransactionSigner {

    // Steps:
    // 1. Set all input-scripts to empty scripts.
    // 2. Set the input's (associated with the inputIndexToBeSigned) unlocking-script to the value of its corresponding output-script from the previous transaction.
    // 3. Append the signatureHashType byte to the serialized transaction bytes.
    // 4. Hash the transaction twice.
    protected byte[] _getBytesForSigning(final SignatureContext signatureContext) {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final Transaction transaction = signatureContext.getTransaction();
        final ScriptSignature.HashType hashType = signatureContext.getHashType();
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(transaction.getVersion());
        mutableTransaction.setHasWitnessData(transaction.hasWitnessData());
        mutableTransaction.setLockTime(transaction.getLockTime());

        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
            mutableTransactionInput.setPreviousOutputIndex(transactionInput.getPreviousOutputIndex());
            mutableTransactionInput.setPreviousOutputTransactionHash(transactionInput.getPreviousOutputTransactionHash());

            final UnlockingScript unlockingScriptForSigning;
            final Boolean shouldSignIndex = signatureContext.shouldInputIndexBeSigned(i);
            if  (shouldSignIndex) {
                final TransactionOutput transactionOutputBeingSpent = signatureContext.getTransactionOutputBeingSpent(i);
                final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();

                final Integer subscriptIndex = signatureContext.getLastCodeSeparatorIndex(i);
                if (subscriptIndex > 0) {
                    final ScriptDeflater scriptDeflater = new ScriptDeflater();

                    // NOTE: Unsure if the LockingScript should receive the same treatment if it has a CodeSeparator.
                    // final MutableScript mutableScript = new MutableScript(signatureContext.getCurrentScript()); //  // TODO: Determine if CodeSeparators are valid in UnlockingScripts...

                    final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();
                    final MutableScript mutableScript = new MutableScript(unlockingScript);

                    mutableScript.subScript(subscriptIndex);
                    mutableScript.removeOperations(Operation.Opcode.CODE_SEPARATOR);
                    // mutableScript.removeData(scriptSignature); // TODO: Other implementations do this... no one is sure why.
                    unlockingScriptForSigning = UnlockingScript.castFrom(mutableScript);
                }
                else {
                    unlockingScriptForSigning = UnlockingScript.castFrom(lockingScript);
                }
            }
            else {
                unlockingScriptForSigning = UnlockingScript.EMPTY_SCRIPT;
            }
            mutableTransactionInput.setUnlockingScript(unlockingScriptForSigning);
            mutableTransactionInput.setSequenceNumber(transactionInput.getSequenceNumber());
            mutableTransaction.addTransactionInput(mutableTransactionInput);
        }

        for (final TransactionOutput transactionOutput : transactionOutputs) {
            final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
            mutableTransactionOutput.setAmount(transactionOutput.getAmount());
            mutableTransactionOutput.setLockingScript(transactionOutput.getLockingScript());
            mutableTransactionOutput.setIndex(transactionOutput.getIndex());
            mutableTransaction.addTransactionOutput(mutableTransactionOutput);
        }

        final ByteArrayBuilder byteArrayBuilder = transactionDeflater.toByteArrayBuilder(mutableTransaction);
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(ByteUtil.byteToInteger(hashType.getValue())), Endian.LITTLE);
        final byte[] bytes = byteArrayBuilder.build();

        return BitcoinUtil.sha256(BitcoinUtil.sha256(bytes));
    }

    public boolean isSignatureValid(final SignatureContext signatureContext, final PublicKey publicKey, final ScriptSignature scriptSignature) {
        final byte[] bytesForSigning = _getBytesForSigning(signatureContext);
        return Secp256k1.verifySignature(scriptSignature.getSignature(), publicKey.getBytes(), bytesForSigning);
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

            if (signatureContext.shouldInputIndexBeSigned(i)) {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(transactionInput);
                mutableTransactionInput.setUnlockingScript(ScriptBuilder.unlockPayToAddress(scriptSignature, privateKey.getPublicKey()));
                mutableTransaction.setTransactionInput(i, mutableTransactionInput);
            }
        }

        return mutableTransaction;
    }
}
