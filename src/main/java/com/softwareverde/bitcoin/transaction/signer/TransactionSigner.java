package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.list.List;

public class TransactionSigner {

    // Steps:
    // 1. Set all input-scripts to empty scripts.
    // 2. Set the input associated with the inputIndexToBeSigned to the value of its corresponding output-script from the previous transaction.
    // 3. Append the signatureHashType byte to the serialized transaction bytes.
    // 4. Hash tx hash twice.
    protected byte[] _getBytesForSigning(final SignatureContext signatureContext, final ScriptSignature.HashType scriptSignatureHashType) {
        final Integer inputIndexToBeSigned = signatureContext.getInputIndexToBeSigned();
        final TransactionOutput transactionOutputBeingSpent = signatureContext.getTransactionOutputBeingSpent();
        final Transaction transaction = signatureContext.getTransaction();

        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(transaction.getVersion());
        mutableTransaction.setHasWitnessData(transaction.hasWitnessData());
        mutableTransaction.setLockTime(transaction.getLockTime());

        final List<? extends TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
            mutableTransactionInput.setPreviousTransactionOutputIndex(transactionInput.getPreviousTransactionOutputIndex());
            mutableTransactionInput.setPreviousTransactionOutputHash(transactionInput.getPreviousTransactionOutputHash());

            final Script unlockingScript;
            if (i == inputIndexToBeSigned) {
                unlockingScript = transactionOutputBeingSpent.getLockingScript();
            }
            else {
                unlockingScript = Script.EMPTY_SCRIPT;
            }
            mutableTransactionInput.setUnlockingScript(unlockingScript);
            mutableTransactionInput.setSequenceNumber(transactionInput.getSequenceNumber());
            mutableTransaction.addTransactionInput(mutableTransactionInput);
        }

        final List<? extends TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
            mutableTransactionOutput.setAmount(transactionOutput.getAmount());
            mutableTransactionOutput.setLockingScript(transactionOutput.getLockingScript());
            mutableTransactionOutput.setIndex(transactionOutput.getIndex());
            mutableTransaction.addTransactionOutput(mutableTransactionOutput);
        }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArrayBuilder byteArrayBuilder = transactionDeflater.toByteArrayBuilder(mutableTransaction);
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(ByteUtil.byteToInteger(scriptSignatureHashType.getValue())), Endian.LITTLE);
        final byte[] bytes = byteArrayBuilder.build();

//        return BitcoinUtil.sha256(BitcoinUtil.sha256(ByteUtil.reverseEndian(bytes)));
        return BitcoinUtil.sha256(BitcoinUtil.sha256(bytes));
    }

    public boolean isSignatureValid(final SignatureContext signatureContext, final byte[] publicKey, final ScriptSignature scriptSignature) {
        final byte[] bytesForSigning = _getBytesForSigning(signatureContext, scriptSignature.getHashType());
        return Secp256k1.verifySignature(scriptSignature.getSignature(), publicKey, bytesForSigning);
    }
}
