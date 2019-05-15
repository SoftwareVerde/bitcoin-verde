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
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.utxo.MutableSpendableTransactionOutput;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

import java.util.Collection;
import java.util.HashMap;

public class Wallet {
    protected static final Long BYTES_PER_TRANSACTION_INPUT = 148L; // P2PKH Inputs are either 147-148 bytes for compressed addresses, or 179-180 bytes for uncompressed addresses.
    protected static final Long BYTES_PER_UNCOMPRESSED_TRANSACTION_INPUT = 180L;
    protected static final Long BYTES_PER_TRANSACTION_OUTPUT = 34L;
    protected static final Long BYTES_PER_TRANSACTION_HEADER = 10L; // This value becomes inaccurate if either the number of inputs or the number out outputs exceeds 252 (The max value of a 1-byte variable length integer)...

    public static Long getDefaultDustThreshold() {
        return (long) ((BYTES_PER_TRANSACTION_OUTPUT + BYTES_PER_TRANSACTION_INPUT) * 3D);
    }

    protected final HashMap<Address, PublicKey> _publicKeys = new HashMap<>();
    protected final HashMap<PublicKey, PrivateKey> _privateKeys = new HashMap<>();
    protected final HashMap<Sha256Hash, Transaction> _transactions = new HashMap<>();

    protected final HashMap<TransactionOutputIdentifier, Sha256Hash> _spentTransactionOutputs = new HashMap<>();
    protected final HashMap<TransactionOutputIdentifier, MutableSpendableTransactionOutput> _transactionOutputs = new HashMap<>();

    protected Double _satoshisPerByteFee = 1D;

    protected Long _calculateDustThreshold(final Long transactionOutputByteCount, final Boolean isCompressed) {
        // "Dust" is defined by XT/ABC as being an output that is less than 1/3 of the fees required to spend that output.
        // Non-spendable TransactionOutputs are exempt from this network rule.
        // For the common default _satoshisPerByteFee (1), the dust threshold is 546 satoshis.

        final long transactionInputByteCount = (isCompressed ? BYTES_PER_TRANSACTION_INPUT : BYTES_PER_UNCOMPRESSED_TRANSACTION_INPUT);
        return (long) ((transactionOutputByteCount + transactionInputByteCount) * _satoshisPerByteFee * 3D);
    }

    protected void _addPrivateKey(final PrivateKey privateKey) {
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

    protected void _addTransaction(final Transaction transaction) {
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
                final MutableSpendableTransactionOutput spendableTransactionOutput = new MutableSpendableTransactionOutput(address, transactionOutputIdentifier, transactionOutput);
                spendableTransactionOutput.setIsSpent(_spentTransactionOutputs.containsKey(transactionOutputIdentifier));
                _transactionOutputs.put(transactionOutputIdentifier, spendableTransactionOutput);
            }
        }
    }

    protected void _reloadTransactions() {
        _spentTransactionOutputs.clear();
        _transactionOutputs.clear();

        final MutableList<Transaction> transactions = new MutableList<>(_transactions.values());
        _transactions.clear();

        for (final Transaction transaction : transactions) {
            _addTransaction(transaction);
        }
    }

    protected Container<Long> _createNewFeeContainer(final Integer newOutputCount) {
        final Container<Long> feesContainer = new Container<>(0L);
        feesContainer.value += (long) (BYTES_PER_TRANSACTION_HEADER * _satoshisPerByteFee);

        final long feeToSpendOneOutput = (long) (BYTES_PER_TRANSACTION_OUTPUT * _satoshisPerByteFee);
        feesContainer.value += (feeToSpendOneOutput * newOutputCount);

        return feesContainer;
    }

    protected List<SpendableTransactionOutput> _getOutputsToSpend(final Long minimumUtxoAmount, final Container<Long> feesToSpendOutputs, final List<TransactionOutputIdentifier> mandatoryTransactionOutputsToSpend) {
        final Long originalFeesToSpendOutputs = feesToSpendOutputs.value;

        final long feeToSpendOneOutput = (long) (BYTES_PER_TRANSACTION_INPUT * _satoshisPerByteFee);
        final long feeToSpendOneUncompressedOutput = (long) (BYTES_PER_UNCOMPRESSED_TRANSACTION_INPUT * _satoshisPerByteFee);

        long selectedUtxoAmount = 0L;
        final MutableList<SpendableTransactionOutput> transactionOutputsToSpend = new MutableList<>();

        final MutableList<SpendableTransactionOutput> unspentTransactionOutputs = new MutableList<>(_transactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {

            { // If this TransactionOutput is one that must be included in this transaction,
              //    then add it to transactionOutputsToSpend, its amount to selectedUtxoAmount,
              //    increase the total fees required for this transaction, and exclude the Utxo
              //    from the possible spendableTransactionOutputs to prevent it from being added twice.
                final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
                if (mandatoryTransactionOutputsToSpend.contains(transactionOutputIdentifier)) {
                    final Address address = spendableTransactionOutput.getAddress();

                    final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
                    selectedUtxoAmount += transactionOutput.getAmount();
                    feesToSpendOutputs.value += (address.isCompressed() ? feeToSpendOneOutput : feeToSpendOneUncompressedOutput);
                    transactionOutputsToSpend.add(spendableTransactionOutput);
                    continue;
                }
            }

            if (! spendableTransactionOutput.isSpent()) {
                unspentTransactionOutputs.add(spendableTransactionOutput);
            }
        }
        unspentTransactionOutputs.sort(SpendableTransactionOutput.AMOUNT_ASCENDING_COMPARATOR);

        for (final SpendableTransactionOutput spendableTransactionOutput : unspentTransactionOutputs) {
            if (selectedUtxoAmount >= (minimumUtxoAmount + feesToSpendOutputs.value)) { break; }

            final Address address = spendableTransactionOutput.getAddress();
            final Long feeToSpendThisOutput = (address.isCompressed() ? feeToSpendOneOutput : feeToSpendOneUncompressedOutput);

            final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
            final Long transactionOutputAmount = transactionOutput.getAmount();

            if (transactionOutputAmount < feeToSpendThisOutput) { continue; } // Exclude spending dust...

            // If the next UnspentTransactionOutput covers the whole transaction cost by itself, then use only that output instead...
            if (transactionOutputAmount >= (minimumUtxoAmount + feeToSpendThisOutput + originalFeesToSpendOutputs)) {
                feesToSpendOutputs.value = (originalFeesToSpendOutputs + feeToSpendThisOutput);
                selectedUtxoAmount = transactionOutputAmount;
                transactionOutputsToSpend.clear();
                transactionOutputsToSpend.add(spendableTransactionOutput);
                break;
            }

            feesToSpendOutputs.value += feeToSpendThisOutput;
            selectedUtxoAmount += transactionOutputAmount;
            transactionOutputsToSpend.add(spendableTransactionOutput);
        }

        if (selectedUtxoAmount < (minimumUtxoAmount + feesToSpendOutputs.value)) {
            Logger.log("INFO: Insufficient funds to fund transaction.");
            feesToSpendOutputs.value = originalFeesToSpendOutputs; // Reset the feesToSpendOutputs container...
            return null;
        }

        return transactionOutputsToSpend;
    }

    protected Transaction _createSignedTransaction(final List<PaymentAmount> paymentAmounts, final List<SpendableTransactionOutput> transactionOutputsToSpend) {
        if (paymentAmounts.isEmpty()) { return null; }
        if (transactionOutputsToSpend == null) { return null; }

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

        final ScriptRunner scriptRunner = new ScriptRunner(null);
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

        return signedTransaction;
    }

    protected Transaction _createSignedTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> mandatoryOutputs) {
        long totalPaymentAmount = 0L;
        for (final PaymentAmount paymentAmount : paymentAmounts) {
            totalPaymentAmount += paymentAmount.amount;
        }

        final int newOutputCount = (paymentAmounts.getSize() + (changeAddress != null ? 1 : 0));

        final Container<Long> feesContainer = _createNewFeeContainer(newOutputCount);
        final List<SpendableTransactionOutput> transactionOutputsToSpend = _getOutputsToSpend(totalPaymentAmount, feesContainer, mandatoryOutputs);
        if (transactionOutputsToSpend == null) { return null; }

        long totalAmountSelected = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : transactionOutputsToSpend) {
            final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
            totalAmountSelected += transactionOutput.getAmount();
        }

        final boolean includedChangeOutput;
        final MutableList<PaymentAmount> paymentAmountsWithChange = new MutableList<>(paymentAmounts.getSize() + 1);
        {
            paymentAmountsWithChange.addAll(paymentAmounts);

            final Long changeAmount = (totalAmountSelected - totalPaymentAmount - feesContainer.value);
            if (changeAddress != null) {
                final Long dustThreshold = _calculateDustThreshold(BYTES_PER_TRANSACTION_OUTPUT, changeAddress.isCompressed());
                includedChangeOutput = (changeAmount >= dustThreshold);
            }
            else {
                includedChangeOutput = false;
            }

            if (includedChangeOutput) {
                paymentAmountsWithChange.add(new PaymentAmount(changeAddress, changeAmount));
            }
        }

        Logger.log("Creating Transaction. Spending " + transactionOutputsToSpend.getSize() + " UTXOs. Creating " + paymentAmountsWithChange.getSize() + " UTXOs. Sending " + totalPaymentAmount + ". Spending " + feesContainer.value + " in fees. " + (includedChangeOutput ? ((totalAmountSelected - totalPaymentAmount - feesContainer.value) + " in change.") : ""));

        final Transaction signedTransaction = _createSignedTransaction(paymentAmountsWithChange, transactionOutputsToSpend);
        if (signedTransaction == null) { return null; }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        Logger.log(signedTransaction.getHash());
        Logger.log(transactionDeflater.toBytes(signedTransaction));

        final Integer transactionByteCount = transactionDeflater.getByteCount(signedTransaction);
        Logger.log("Transaction Bytes Count: " + transactionByteCount + " (" + (feesContainer.value / transactionByteCount.floatValue()) + " sats/byte)");

        return signedTransaction;
    }

    public void setSatoshisPerByteFee(final Double satoshisPerByte) {
        _satoshisPerByteFee = satoshisPerByte;
    }

    public Long getDustThreshold(final Boolean addressIsCompressed) {
        return _calculateDustThreshold(BYTES_PER_TRANSACTION_OUTPUT, addressIsCompressed);
    }

    public synchronized void addPrivateKey(final PrivateKey privateKey) {
        _addPrivateKey(privateKey);
        _reloadTransactions();
    }

    public synchronized void addPrivateKeys(final List<PrivateKey> privateKeys) {
        for (final PrivateKey privateKey : privateKeys) {
            _addPrivateKey(privateKey);
        }
        _reloadTransactions();
    }

    public synchronized void addTransaction(final Transaction transaction) {
        _addTransaction(transaction);
    }

    public synchronized void markTransactionOutputSpent(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
        final MutableSpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
        if (spendableTransactionOutput == null) { return; }

        spendableTransactionOutput.setIsSpent(true);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount) {
        final Container<Long> feesContainer = _createNewFeeContainer(newTransactionOutputCount);
        final List<TransactionOutputIdentifier> mandatoryOutputs = new MutableList<>(0);
        final List<SpendableTransactionOutput> spendableTransactionOutputs = _getOutputsToSpend(desiredSpendAmount, feesContainer, mandatoryOutputs);
        if (spendableTransactionOutputs == null) { return null; }

        final MutableList<TransactionOutputIdentifier> transactionOutputs = new MutableList<>(spendableTransactionOutputs.getSize());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            transactionOutputs.add(spendableTransactionOutput.getIdentifier());
        }
        return transactionOutputs;
    }

    public Long calculateFees(final Integer newOutputCount, final Integer outputsBeingSpentCount) {
        final Container<Long> feeContainer = _createNewFeeContainer(newOutputCount);
        feeContainer.value += (long) ((BYTES_PER_TRANSACTION_INPUT * _satoshisPerByteFee) * outputsBeingSpentCount);
        return feeContainer.value;
    }

    public synchronized Transaction createTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress) {
        final List<TransactionOutputIdentifier> mandatoryTransactionOutputsToSpend = new MutableList<>(0);
        return _createSignedTransaction(paymentAmounts, changeAddress, mandatoryTransactionOutputsToSpend);
    }

    public synchronized Transaction createTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> transactionOutputIdentifiersToSpend) {
        return _createSignedTransaction(paymentAmounts, changeAddress, transactionOutputIdentifiersToSpend);
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

    public synchronized SpendableTransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionOutputs.get(transactionOutputIdentifier);
    }

    public synchronized List<SpendableTransactionOutput> getTransactionOutputs() {
        final Collection<? extends SpendableTransactionOutput> spendableTransactionOutputs = _transactionOutputs.values();
        final ImmutableListBuilder<SpendableTransactionOutput> transactionOutputs = new ImmutableListBuilder<>(spendableTransactionOutputs.size());
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
