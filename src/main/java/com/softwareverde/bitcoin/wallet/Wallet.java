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
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
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
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.utxo.MutableSpendableTransactionOutput;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;

import java.util.Collection;
import java.util.HashMap;

public class Wallet {
    protected final HashMap<Address, PublicKey> _publicKeys = new HashMap<Address, PublicKey>();
    protected final HashMap<PublicKey, PrivateKey> _privateKeys = new HashMap<PublicKey, PrivateKey>();
    protected final HashMap<Sha256Hash, Transaction> _transactions = new HashMap<Sha256Hash, Transaction>();

    protected final HashMap<TransactionOutputIdentifier, Sha256Hash> _spentTransactionOutputs = new HashMap<TransactionOutputIdentifier, Sha256Hash>();
    protected final HashMap<TransactionOutputIdentifier, MutableSpendableTransactionOutput> _transactionOutputs = new HashMap<TransactionOutputIdentifier, MutableSpendableTransactionOutput>();

    protected final Long _bytesPerTransactionInput = 150L;
    protected final Long _bytesPerTransactionOutput = 35L;
    protected final Long _bytesPerTransactionMetadata = 8L;

    protected Double _satoshisPerByteFee = 5D;

    public void setSatoshisPerByteFee(final Double satoshisPerByte) {
        _satoshisPerByteFee = satoshisPerByte;
    }

    public synchronized void addPrivateKey(final PrivateKey privateKey) {
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

    public synchronized void addTransaction(final Transaction transaction) {
        final Transaction constTransaction = transaction.asConst();
        final Sha256Hash transactionHash = constTransaction.getHash();
        _transactions.put(transactionHash.asConst(), constTransaction);

        // Mark outputs as spent, if any...
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            _spentTransactionOutputs.put(transactionOutputIdentifier, transactionHash);

            final MutableSpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
            if (spendableTransactionOutput == null) { continue; }

            spendableTransactionOutput.setIsSpent(true);
        }

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
                final MutableSpendableTransactionOutput spendableTransactionOutput = new MutableSpendableTransactionOutput(transactionOutputIdentifier, transactionOutput);
                spendableTransactionOutput.setIsSpent(_spentTransactionOutputs.containsKey(transactionOutputIdentifier));
                _transactionOutputs.put(transactionOutputIdentifier, spendableTransactionOutput);
            }
        }
    }

    public synchronized void markTransactionOutputSpent(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
        final MutableSpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
        if (spendableTransactionOutput == null) { return; }

        spendableTransactionOutput.setIsSpent(true);
    }

    public synchronized Transaction createTransaction(final List<PaymentAmount> paymentAmounts) {
        if (paymentAmounts.isEmpty()) { return null; }

        final MutableList<SpendableTransactionOutput> unspentTransactionOutputs = new MutableList<SpendableTransactionOutput>(_transactionOutputs.size());
        for (final SpendableTransactionOutput transactionOutput : _transactionOutputs.values()) {
            if (! transactionOutput.isSpent()) {
                unspentTransactionOutputs.add(transactionOutput);
            }
        }
        unspentTransactionOutputs.sort(SpendableTransactionOutput.AMOUNT_ASCENDING_COMPARATOR);

        long totalPaymentAmount = 0L;
        long requiredAmount = 0L;
        for (final PaymentAmount paymentAmount : paymentAmounts) {
            requiredAmount += (long) (_bytesPerTransactionOutput * _satoshisPerByteFee);
            requiredAmount += paymentAmount.amount;

            totalPaymentAmount += paymentAmount.amount;
        }

        requiredAmount += (long) (_bytesPerTransactionMetadata * _satoshisPerByteFee);

        final MutableList<SpendableTransactionOutput> transactionOutputsToSpend = new MutableList<SpendableTransactionOutput>();
        long currentAmount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : unspentTransactionOutputs) {
            if (currentAmount >= requiredAmount) { break; }

            final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();

            final long feeToSpendOutput = (long) (_satoshisPerByteFee * _bytesPerTransactionInput);
            if (transactionOutput.getAmount() < feeToSpendOutput) { continue; }

            requiredAmount += (long) (_bytesPerTransactionInput * _satoshisPerByteFee);
            currentAmount += transactionOutput.getAmount();
            transactionOutputsToSpend.add(spendableTransactionOutput);
        }

        if (currentAmount < requiredAmount) {
            Logger.log("INFO: Insufficient funds to fund transaction.");
            return null;
        }

        final long calculatedFees = (requiredAmount - totalPaymentAmount);
        Logger.log("Creating Transaction. Spending " + transactionOutputsToSpend.getSize() + " UTXOs. Creating " + paymentAmounts.getSize() + " UTXOs. Sending " + totalPaymentAmount + ". Spending " + calculatedFees + " in fees. " + (currentAmount - totalPaymentAmount - calculatedFees) + " in change.");

        final MutableTransaction transaction = new MutableTransaction();
        transaction.setVersion(Transaction.VERSION);
        transaction.setLockTime(LockTime.MIN_TIMESTAMP);

        for (int i = 0; i < transactionOutputsToSpend.getSize(); ++i) {
            final SpendableTransactionOutput spendableTransactionOutput = transactionOutputsToSpend.get(i);

            final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
            final Transaction transactionBeingSpent = _transactions.get(transactionOutputIdentifier.getTransactionHash());
            final Integer transactionOutputBeingSpentIndex = transactionOutputIdentifier.getOutputIndex();

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
                final TransactionOutput transactionOutputBeingSpent = spendableTransactionOutput.getTransactionOutput();

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
        Logger.log(signedTransaction.getHash());
        Logger.log(transactionDeflater.toBytes(signedTransaction));

        final ScriptRunner scriptRunner = new ScriptRunner();
        final List<TransactionInput> signedTransactionInputs = signedTransaction.getTransactionInputs();
        for (int i = 0; i < signedTransactionInputs.getSize(); ++i) {
            final TransactionInput signedTransactionInput = signedTransactionInputs.get(i);
            final TransactionOutput transactionOutputBeingSpent;
            {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(signedTransactionInput.getPreviousOutputTransactionHash(), signedTransactionInput.getPreviousOutputIndex());
                final SpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
                transactionOutputBeingSpent = spendableTransactionOutput.getTransactionOutput();
            }

            final MutableContext context = MutableContext.getContextForVerification(signedTransaction, i, transactionOutputBeingSpent);
            final Boolean outputIsUnlocked = scriptRunner.runScript(transactionOutputBeingSpent.getLockingScript(), signedTransactionInput.getUnlockingScript(), context);

            if (! outputIsUnlocked) {
                Logger.log("NOTICE: Error signing transaction.");
                return null;
            }
        }

        final Integer transactionByteCount = transactionDeflater.getByteCount(signedTransaction);
        Logger.log("Transaction Bytes Count: " + transactionByteCount + " (" + (calculatedFees / transactionByteCount.floatValue()) + " sats/byte)");

        return signedTransaction;
    }

    public synchronized MutableBloomFilter generateBloomFilter() {
        final AddressInflater addressInflater = new AddressInflater();

        final Collection<PrivateKey> privateKeys = _privateKeys.values();

        final long itemCount;
        {
            final int privateKeyCount = privateKeys.size();
            final int estimatedItemCount = (privateKeyCount * 4);
            itemCount = (int) (Math.pow(2, (BitcoinUtil.log2(estimatedItemCount) + 1)));
        }

        final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance(itemCount, 0.01D);

        for (final PrivateKey privateKey : privateKeys) {
            final PublicKey publicKey = privateKey.getPublicKey();

            // Add sending matchers...
            bloomFilter.addItem(publicKey.decompress());
            bloomFilter.addItem(publicKey.compress());

            // Add receiving matchers...
            bloomFilter.addItem(addressInflater.fromPrivateKey(privateKey));
            bloomFilter.addItem(addressInflater.compressedFromPrivateKey(privateKey));
        }

        return bloomFilter;
    }

    public synchronized List<SpendableTransactionOutput> getTransactionOutputs() {
        final Collection<? extends SpendableTransactionOutput> spendableTransactionOutputs = _transactionOutputs.values();
        final ImmutableListBuilder<SpendableTransactionOutput> transactionOutputs = new ImmutableListBuilder<SpendableTransactionOutput>(spendableTransactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            transactionOutputs.add(spendableTransactionOutput);
        }
        return transactionOutputs.build();
    }

    public synchronized Long getBalance() {
        long amount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            if (! spendableTransactionOutput.isSpent()) {
                final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
                amount += transactionOutput.getAmount();
            }
        }
        return amount;
    }
}
