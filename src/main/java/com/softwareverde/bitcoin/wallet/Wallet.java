package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;

import java.util.Comparator;
import java.util.HashMap;

public class Wallet {
    protected static class SpendableTransactionOutput {
        public final TransactionOutputIdentifier identifier;
        public final TransactionOutput transactionOutput;
        public Boolean isSpent;

        public SpendableTransactionOutput(final TransactionOutputIdentifier identifier, final TransactionOutput transactionOutput) {
            this.identifier = identifier;
            this.transactionOutput = transactionOutput;
            this.isSpent = false;
        }

        public SpendableTransactionOutput(final TransactionOutputIdentifier identifier, final TransactionOutput transactionOutput, final Boolean isSpent) {
            this.identifier = identifier;
            this.transactionOutput = transactionOutput;
            this.isSpent = isSpent;
        }
    }

    protected static Comparator<SpendableTransactionOutput> _spendableTransactionOutputComparatorAmountAscending = new Comparator<SpendableTransactionOutput>() {
        @Override
        public int compare(final SpendableTransactionOutput transactionOutput0, final SpendableTransactionOutput transactionOutput1) {
            return transactionOutput0.transactionOutput.getAmount().compareTo(transactionOutput1.transactionOutput.getAmount());
        }
    };

    protected final HashMap<Address, PublicKey> _publicKeys = new HashMap<Address, PublicKey>();
    protected final HashMap<PublicKey, PrivateKey> _privateKeys = new HashMap<PublicKey, PrivateKey>();
    protected final HashMap<Sha256Hash, Transaction> _transactions = new HashMap<Sha256Hash, Transaction>();
    protected final HashMap<TransactionOutputIdentifier, SpendableTransactionOutput> _transactionOutputs = new HashMap<TransactionOutputIdentifier, SpendableTransactionOutput>();

    protected Double _satoshisPerByteFee = 10D;

    protected Long _bytesPerTransactionInput = 150L;
    protected Long _bytesPerTransactionOutput = 35L;
    protected Long _bytesPerTransactionMetadata = 8L;

    public void setSatoshisPerByteFee(final Double satoshisPerByte) {
        _satoshisPerByteFee = satoshisPerByte;
    }

    public void addPrivateKey(final PrivateKey privateKey) {
        final PrivateKey constPrivateKey = privateKey.asConst();
        final PublicKey publicKey = constPrivateKey.getPublicKey();
        final PublicKey compressedPublicKey = publicKey.compress();
        final PublicKey decompressedPublicKey = publicKey.decompress();

        final AddressInflater addressInflater = new AddressInflater();
        final Address decompressedAddress = addressInflater.fromPrivateKey(constPrivateKey);
        final Address compressedAddress = addressInflater.compressedFromPrivateKey(constPrivateKey);

        _privateKeys.put(compressedPublicKey.asConst(), constPrivateKey);
        _privateKeys.put(decompressedPublicKey.asConst(), constPrivateKey);

        _publicKeys.put(compressedAddress, compressedPublicKey);
        _publicKeys.put(decompressedAddress, decompressedPublicKey);
    }

    public void addTransaction(final Transaction transaction) {
        final Transaction constTransaction = transaction.asConst();
        final Sha256Hash transactionHash = constTransaction.getHash();
        _transactions.put(transactionHash.asConst(), constTransaction);

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final List<TransactionOutput> transactionOutputs = constTransaction.getTransactionOutputs();
        for (int transactionOutputIndex = 0; transactionOutputIndex < transactionOutputs.getSize(); ++transactionOutputIndex) {
            final TransactionOutput transactionOutput = transactionOutputs.get(transactionOutputIndex);

            final LockingScript lockingScript = transactionOutput.getLockingScript();
            final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
            if (scriptType == null) { continue; }

            final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
            if (address == null) { continue; }

            if (_publicKeys.containsKey(address)) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
                final SpendableTransactionOutput spendableTransactionOutput = new SpendableTransactionOutput(transactionOutputIdentifier, transactionOutput);
                _transactionOutputs.put(transactionOutputIdentifier, spendableTransactionOutput);
            }
        }
    }

    public void markTransactionOutputSpent(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
        final SpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
        if (spendableTransactionOutput == null) { return; }

        spendableTransactionOutput.isSpent = true;
    }

    public Transaction createTransaction(final List<PaymentAmount> paymentAmounts) {
        if (paymentAmounts.isEmpty()) { return null; }

        final MutableList<SpendableTransactionOutput> unspentTransactionOutputs = new MutableList<SpendableTransactionOutput>(_transactionOutputs.size());
        for (final SpendableTransactionOutput transactionOutput : _transactionOutputs.values()) {
            if (! transactionOutput.isSpent) {
                unspentTransactionOutputs.add(transactionOutput);
            }
        }
        unspentTransactionOutputs.sort(_spendableTransactionOutputComparatorAmountAscending);

        Long totalPaymentAmount = 0L;
        Long requiredAmount = 0L;
        for (final PaymentAmount paymentAmount : paymentAmounts) {
            requiredAmount += (long) (_bytesPerTransactionOutput * _satoshisPerByteFee);
            requiredAmount += paymentAmount.amount;

            totalPaymentAmount += paymentAmount.amount;
        }

        requiredAmount += (long) (_bytesPerTransactionMetadata * _satoshisPerByteFee);

        final MutableList<SpendableTransactionOutput> transactionOutputsToSpend = new MutableList<SpendableTransactionOutput>();
        Long currentAmount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : unspentTransactionOutputs) {
            if (currentAmount >= requiredAmount) { break; }

            final TransactionOutput transactionOutput = spendableTransactionOutput.transactionOutput;

            final Long feeToSpendOutput = (long) (_satoshisPerByteFee * _bytesPerTransactionInput);
            if (transactionOutput.getAmount() < feeToSpendOutput) { continue; }

            requiredAmount += (long) (_bytesPerTransactionInput * _satoshisPerByteFee);
            currentAmount += transactionOutput.getAmount();
            transactionOutputsToSpend.add(spendableTransactionOutput);
        }

        if (currentAmount < requiredAmount) {
            Logger.log("INFO: Insufficient funds to fund transaction.");
            return null;
        }

        final Long calculatedFees = (requiredAmount - totalPaymentAmount);
        Logger.log("Creating Transaction. Spending " + transactionOutputsToSpend.getSize() + " UTXOs. Creating " + paymentAmounts.getSize() + " UTXOs. Sending " + totalPaymentAmount + ". Spending " + calculatedFees + " in fees. " + (currentAmount - totalPaymentAmount - calculatedFees) + " in change.");

        final MutableTransaction transaction = new MutableTransaction();
        transaction.setVersion(Transaction.VERSION);
        transaction.setLockTime(LockTime.MIN_TIMESTAMP);

        for (int i = 0; i < transactionOutputsToSpend.getSize(); ++i) {
            final SpendableTransactionOutput spendableTransactionOutput = transactionOutputsToSpend.get(i);

            final Transaction transactionBeingSpent = _transactions.get(spendableTransactionOutput.identifier.getTransactionHash());
            final Integer transactionOutputBeingSpentIndex = spendableTransactionOutput.identifier.getOutputIndex();

            { // Transaction Input...
                final MutableTransactionInput transactionInput = new MutableTransactionInput();
                transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                transactionInput.setPreviousOutputTransactionHash(transactionBeingSpent.getHash());
                transactionInput.setPreviousOutputIndex(transactionOutputBeingSpentIndex);
                transactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                transaction.addTransactionInput(transactionInput);
            }
        }

        for (int i = 0; i < paymentAmounts.getSize(); ++i) {
            final PaymentAmount paymentAmount = paymentAmounts.get(i);

            { // Transaction Output...
                final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
                transactionOutput.setIndex(i);
                transactionOutput.setAmount(paymentAmount.amount);
                transactionOutput.setLockingScript(ScriptBuilder.payToAddress(paymentAmount.address));
                transaction.addTransactionOutput(transactionOutput);
            }
        }

        final TransactionSigner transactionSigner = new TransactionSigner();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final Transaction signedTransaction;
        {
            Transaction transactionBeingSigned = transaction;
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
            for (int i = 0; i < transactionInputs.getSize(); ++i) {
                final SpendableTransactionOutput spendableTransactionOutput = transactionOutputsToSpend.get(i);
                final TransactionOutput transactionOutputBeingSpent = spendableTransactionOutput.transactionOutput;

                final PrivateKey privateKey;
                final Boolean useCompressedPublicKey;
                {
                    final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
                    final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                    final Address addressBeingSpent = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                    final PublicKey publicKey = _publicKeys.get(addressBeingSpent);
                    privateKey = _privateKeys.get(publicKey);
                    useCompressedPublicKey = publicKey.isCompressed();
                }

                final SignatureContext signatureContext = new SignatureContext(transactionBeingSigned, new HashType(Mode.SIGNATURE_HASH_ALL, true, true));
                signatureContext.setInputIndexBeingSigned(i);
                signatureContext.setShouldSignInputScript(i, true, transactionOutputBeingSpent);
                transactionBeingSigned = transactionSigner.signTransaction(signatureContext, privateKey, useCompressedPublicKey);
            }

            signedTransaction = transactionBeingSigned;
        }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        System.out.println(signedTransaction.getHash());
        System.out.println(transactionDeflater.toBytes(signedTransaction));

        final ScriptRunner scriptRunner = new ScriptRunner();
        final List<TransactionInput> signedTransactionInputs = signedTransaction.getTransactionInputs();
        for (int i = 0; i < signedTransactionInputs.getSize(); ++i) {
            final TransactionInput signedTransactionInput = signedTransactionInputs.get(i);
            final TransactionOutput transactionOutputBeingSpent;
            {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(signedTransactionInput.getPreviousOutputTransactionHash(), signedTransactionInput.getPreviousOutputIndex());
                final SpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
                transactionOutputBeingSpent = spendableTransactionOutput.transactionOutput;
            }

            final MutableContext context = MutableContext.getContextForVerification(signedTransaction, i, transactionOutputBeingSpent);
            final Boolean outputIsUnlocked = scriptRunner.runScript(transactionOutputBeingSpent.getLockingScript(), signedTransactionInput.getUnlockingScript(), context);

            if (! outputIsUnlocked) {
                Logger.log("NOTICE: Error signing transaction.");
                return null;
            }
        }

        return signedTransaction;
    }
}
