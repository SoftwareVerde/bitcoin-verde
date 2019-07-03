package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.slp.SlpUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.slp.send.MutableSlpSendScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.slp.ImmutableSlpToken;
import com.softwareverde.bitcoin.wallet.slp.SlpPaymentAmount;
import com.softwareverde.bitcoin.wallet.slp.SlpToken;
import com.softwareverde.bitcoin.wallet.utxo.MutableSpendableTransactionOutput;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.*;

public class Wallet {
    protected static final Long BYTES_PER_TRANSACTION_INPUT = 148L; // P2PKH Inputs are either 147-148 bytes for compressed addresses, or 179-180 bytes for uncompressed addresses.
    protected static final Long BYTES_PER_UNCOMPRESSED_TRANSACTION_INPUT = 180L;
    protected static final Long BYTES_PER_TRANSACTION_OUTPUT = 34L;
    protected static final Long BYTES_PER_TRANSACTION_HEADER = 10L; // This value becomes inaccurate if either the number of inputs or the number out outputs exceeds 252 (The max value of a 1-byte variable length integer)...

    public static Long getDefaultDustThreshold() {
        return (long) ((BYTES_PER_TRANSACTION_OUTPUT + BYTES_PER_TRANSACTION_INPUT) * 3D);
    }

    protected final HashMap<Address, PublicKey> _publicKeys = new HashMap<Address, PublicKey>();
    protected final HashMap<PublicKey, PrivateKey> _privateKeys = new HashMap<PublicKey, PrivateKey>();
    protected final HashMap<Sha256Hash, Transaction> _transactions = new HashMap<Sha256Hash, Transaction>();

    protected final HashMap<TransactionOutputIdentifier, Sha256Hash> _spentTransactionOutputs = new HashMap<TransactionOutputIdentifier, Sha256Hash>();
    protected final HashMap<TransactionOutputIdentifier, MutableSpendableTransactionOutput> _transactionOutputs = new HashMap<TransactionOutputIdentifier, MutableSpendableTransactionOutput>();
    protected BloomFilter _cachedBloomFilter = null;

    protected final MedianBlockTime _medianBlockTime;
    protected Double _satoshisPerByteFee = 1D;

    protected static <T> Tuple<T, Long> removeClosestTupleAmount(final MutableList<Tuple<T, Long>> sortedAvailableAmounts, final Long desiredAmount) {
        int selectedIndex = -1;
        Tuple<T, Long> lastAmount = null;
        for (final Tuple<T, Long> tuple : sortedAvailableAmounts) {
            if (lastAmount == null) {
                lastAmount = tuple;
                selectedIndex += 1;
                continue;
            }

            final long previousDifference = Math.abs(lastAmount.second - desiredAmount);
            final long currentDifference = Math.abs(tuple.second - desiredAmount);
            if (previousDifference < currentDifference) { break; }

            lastAmount = tuple;
            selectedIndex += 1;
        }

        if (selectedIndex >= 0) {
            sortedAvailableAmounts.remove(selectedIndex);
        }

        return lastAmount;
    }

    protected SlpTokenId _getSlpTokenId(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();

        final Transaction transaction = _transactions.get(transactionHash);
        if (transaction == null) {
            throw new RuntimeException("Unable to find TransactionOutput: " + transactionOutputIdentifier);
        }

        return SlpUtil.getTokenId(transaction);
    }

    protected Boolean _isSlpTokenOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

        final Transaction transaction = _transactions.get(transactionHash);
        if (transaction == null) {
            throw new RuntimeException("Unable to find TransactionOutput: " + transactionOutputIdentifier);
        }

        return SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
    }

    protected Boolean _outputContainsSpendableSlpTokens(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

        final Transaction transaction = _transactions.get(transactionHash);
        if (transaction == null) {
            throw new RuntimeException("Unable to find TransactionOutput: " + transactionOutputIdentifier);
        }

        return SlpUtil.outputContainsSpendableSlpTokens(transaction, transactionOutputIndex);
    }

    public synchronized List<SlpToken> _getSlpTokens(final SlpTokenId matchingSlpTokenId) {
        final Collection<? extends SpendableTransactionOutput> spendableTransactionOutputs = _transactionOutputs.values();
        final ImmutableListBuilder<SlpToken> slpTokens = new ImmutableListBuilder<SlpToken>(spendableTransactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

            final Transaction transaction = _transactions.get(transactionHash);
            if (transaction == null) { continue; }

            final boolean outputContainsTokens = SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
            if (! outputContainsTokens) { continue; }

            final SlpTokenId tokenId = SlpUtil.getTokenId(transaction);
            if (matchingSlpTokenId != null) {
                if (! Util.areEqual(matchingSlpTokenId, tokenId)) {
                    continue;
                }
            }

            final Long tokenAmount = SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
            final Boolean isBatonHolder = SlpUtil.isSlpTokenBatonHolder(transaction, transactionOutputIndex);

            final SlpToken slpToken = new ImmutableSlpToken(tokenId, tokenAmount, spendableTransactionOutput, isBatonHolder);
            slpTokens.add(slpToken);
        }
        return slpTokens.build();
    }

    protected Long _getSlpTokenAmount(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Transaction transaction = _transactions.get(transactionOutputIdentifier.getTransactionHash());
        if (transaction == null) { return null; }

        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
        return SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
    }

    protected Long _calculateDustThreshold(final Long transactionOutputByteCount, final Boolean addressIsCompressed) {
        // "Dust" is defined by XT/ABC as being an output that is less than 1/3 of the fees required to spend that output.
        // Non-spendable TransactionOutputs are exempt from this network rule.
        // For the common default _satoshisPerByteFee (1), the dust threshold is 546 satoshis.

        final long transactionInputByteCount = (addressIsCompressed ? BYTES_PER_TRANSACTION_INPUT : BYTES_PER_UNCOMPRESSED_TRANSACTION_INPUT);
        return (long) ((transactionOutputByteCount + transactionInputByteCount) * _satoshisPerByteFee * 3D);
    }

    /**
     * Returns the fee required to add the opReturnScript as a TransactionOutput, modified by _satoshisPerByteFee.
     *  This calculation includes the entire TransactionOutput, not just its Script component.
     */
    protected Long _calculateOpReturnScriptFee(final LockingScript opReturnScript) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setIndex(0);
        transactionOutput.setAmount(0L);
        transactionOutput.setLockingScript(opReturnScript);

        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final Integer outputByteCount = transactionOutputDeflater.getByteCount(transactionOutput);
        return (long) (outputByteCount * _satoshisPerByteFee);
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
        _transactions.put(transactionHash, constTransaction);

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
                final boolean isSpent = _spentTransactionOutputs.containsKey(transactionOutputIdentifier);

                final MutableSpendableTransactionOutput spendableTransactionOutput = new MutableSpendableTransactionOutput(address, transactionOutputIdentifier, transactionOutput);
                spendableTransactionOutput.setIsSpent(isSpent);
                _transactionOutputs.put(transactionOutputIdentifier, spendableTransactionOutput);
            }
        }
    }

    protected void _reloadTransactions() {
        _spentTransactionOutputs.clear();
        _transactionOutputs.clear();

        final MutableList<Transaction> transactions = new MutableList<Transaction>(_transactions.values());
        _transactions.clear();

        for (final Transaction transaction : transactions) {
            _addTransaction(transaction);
        }
    }

    protected Container<Long> _createNewFeeContainer(final Integer newOutputCount, final LockingScript opReturnScript) {
        final Container<Long> feesContainer = new Container<Long>(0L);
        feesContainer.value += (long) (BYTES_PER_TRANSACTION_HEADER * _satoshisPerByteFee);

        final long feeToSpendOneOutput = (long) (BYTES_PER_TRANSACTION_OUTPUT * _satoshisPerByteFee);
        feesContainer.value += (feeToSpendOneOutput * newOutputCount);

        if (opReturnScript != null) {
            feesContainer.value += _calculateOpReturnScriptFee(opReturnScript);
        }

        return feesContainer;
    }

    protected List<SpendableTransactionOutput> _getOutputsToSpend(final Long minimumUtxoAmount, final Container<Long> feesToSpendOutputs, final List<TransactionOutputIdentifier> mandatoryTransactionOutputsToSpend) {
        final Long originalFeesToSpendOutputs = feesToSpendOutputs.value;

        final long feeToSpendOneOutput = (long) (BYTES_PER_TRANSACTION_INPUT * _satoshisPerByteFee);
        final long feeToSpendOneUncompressedOutput = (long) (BYTES_PER_UNCOMPRESSED_TRANSACTION_INPUT * _satoshisPerByteFee);

        long selectedUtxoAmount = 0L;
        final MutableList<SpendableTransactionOutput> transactionOutputsToSpend = new MutableList<SpendableTransactionOutput>();

        final MutableList<SpendableTransactionOutput> unspentTransactionOutputs = new MutableList<SpendableTransactionOutput>(_transactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();

            // If this TransactionOutput is one that must be included in this transaction,
            //  then add it to transactionOutputsToSpend, its amount to selectedUtxoAmount,
            //  increase the total fees required for this transaction, and exclude the Utxo
            //  from the possible spendableTransactionOutputs to prevent it from being added twice.
            if (mandatoryTransactionOutputsToSpend.contains(transactionOutputIdentifier)) {
                final Address address = spendableTransactionOutput.getAddress();

                final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
                selectedUtxoAmount += transactionOutput.getAmount();
                feesToSpendOutputs.value += (address.isCompressed() ? feeToSpendOneOutput : feeToSpendOneUncompressedOutput);
                transactionOutputsToSpend.add(spendableTransactionOutput);
                continue;
            }

            // Avoid spending tokens as regular BCH...
            if (_isSlpTokenOutput(transactionOutputIdentifier)) { continue; }

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
                // Remove any non-mandatory outputs, then add the output covering the cost...
                final Iterator<SpendableTransactionOutput> mutableIterator = transactionOutputsToSpend.mutableIterator();
                while (mutableIterator.hasNext()) {
                    final SpendableTransactionOutput selectedTransactionOutput = mutableIterator.next();
                    final TransactionOutputIdentifier transactionOutputIdentifier = selectedTransactionOutput.getIdentifier();
                    if (! mandatoryTransactionOutputsToSpend.contains(transactionOutputIdentifier)) {
                        mutableIterator.remove();

                        // Subtract the fee for spending this output...
                        final Address addressBeingRemoved = selectedTransactionOutput.getAddress();
                        final Long feeToSpendRemovedOutput = (addressBeingRemoved.isCompressed() ? feeToSpendOneOutput : feeToSpendOneUncompressedOutput);
                        feesToSpendOutputs.value -= feeToSpendRemovedOutput;
                    }
                }

                feesToSpendOutputs.value += feeToSpendThisOutput;
                selectedUtxoAmount = transactionOutputAmount;
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

    protected List<TransactionOutputIdentifier> _getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final LockingScript opReturnScript) {
        final Container<Long> feesContainer = _createNewFeeContainer(newTransactionOutputCount, opReturnScript);
        final List<TransactionOutputIdentifier> mandatoryOutputs = new MutableList<TransactionOutputIdentifier>(0);
        final List<SpendableTransactionOutput> spendableTransactionOutputs = _getOutputsToSpend(desiredSpendAmount, feesContainer, mandatoryOutputs);
        if (spendableTransactionOutputs == null) { return null; }

        final MutableList<TransactionOutputIdentifier> transactionOutputs = new MutableList<TransactionOutputIdentifier>(spendableTransactionOutputs.getSize());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            transactionOutputs.add(spendableTransactionOutput.getIdentifier());
        }
        return transactionOutputs;
    }

    protected Transaction _createSignedTransaction(final List<PaymentAmount> paymentAmounts, final List<SpendableTransactionOutput> transactionOutputsToSpend, final LockingScript opReturnScript) {
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

        int transactionOutputIndex = 0;
        if (opReturnScript != null) {
            { // OpReturn TransactionOutput...
                final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
                transactionOutput.setIndex(0);
                transactionOutput.setAmount(0L);
                transactionOutput.setLockingScript(opReturnScript);
                transaction.addTransactionOutput(transactionOutput);
            }

            transactionOutputIndex += 1;
        }

        for (int i = 0; i < paymentAmounts.getSize(); ++i) {
            final PaymentAmount paymentAmount = paymentAmounts.get(i);

            { // TransactionOutput...
                final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
                transactionOutput.setIndex(transactionOutputIndex);
                transactionOutput.setAmount(paymentAmount.amount);
                transactionOutput.setLockingScript(ScriptBuilder.payToAddress(paymentAmount.address));
                transaction.addTransactionOutput(transactionOutput);
            }

            transactionOutputIndex += 1;
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

        final ScriptRunner scriptRunner = new ScriptRunner(_medianBlockTime);
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

    protected Transaction _createSignedTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> mandatoryOutputs, final LockingScript opReturnScript) {
        long totalPaymentAmount = 0L;
        for (final PaymentAmount paymentAmount : paymentAmounts) {
            totalPaymentAmount += paymentAmount.amount;
        }

        final int newOutputCount = (paymentAmounts.getSize() + (changeAddress != null ? 1 : 0));

        final Container<Long> feesContainer = _createNewFeeContainer(newOutputCount, opReturnScript);
        final List<SpendableTransactionOutput> transactionOutputsToSpend = _getOutputsToSpend(totalPaymentAmount, feesContainer, mandatoryOutputs);
        if (transactionOutputsToSpend == null) { return null; }

        long totalAmountSelected = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : transactionOutputsToSpend) {
            final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
            totalAmountSelected += transactionOutput.getAmount();
        }

        final boolean includedChangeOutput;
        final MutableList<PaymentAmount> paymentAmountsWithChange = new MutableList<PaymentAmount>(paymentAmounts.getSize() + 1);
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
                // Check paymentAmountsWithChange for an existing output using the change address...
                Integer changePaymentAmountIndex = null;
                for (int i = 0; i < paymentAmountsWithChange.getSize(); ++i) {
                    final PaymentAmount paymentAmount = paymentAmountsWithChange.get(i);
                    if (Util.areEqual(changeAddress, paymentAmount.address)) {
                        changePaymentAmountIndex = i;
                        break;
                    }
                }

                if (changePaymentAmountIndex == null) {
                    // The changeAddress was not listed as a PaymentAmount, so create a new one for the change...
                    paymentAmountsWithChange.add(new PaymentAmount(changeAddress, changeAmount));
                }
                else {
                    // The changeAddress already existed as a PaymentAmount; copy it with the additional change, and maintain the SLP Amount if provided...
                    final PaymentAmount paymentAmount = paymentAmountsWithChange.get(changePaymentAmountIndex);

                    final PaymentAmount newPaymentAmount;
                    if (paymentAmount instanceof SlpPaymentAmount) {
                        newPaymentAmount = new SlpPaymentAmount(paymentAmount.address, (paymentAmount.amount + changeAmount), ((SlpPaymentAmount) paymentAmount).tokenAmount);
                    }
                    else {
                        newPaymentAmount = new PaymentAmount(paymentAmount.address, (paymentAmount.amount + changeAmount));
                    }
                    paymentAmountsWithChange.set(changePaymentAmountIndex, newPaymentAmount);
                }
            }
        }

        Logger.log("Creating Transaction. Spending " + transactionOutputsToSpend.getSize() + " UTXOs. Creating " + paymentAmountsWithChange.getSize() + " UTXOs. Sending " + totalPaymentAmount + ". Spending " + feesContainer.value + " in fees. " + (includedChangeOutput ? ((totalAmountSelected - totalPaymentAmount - feesContainer.value) + " in change.") : ""));

        final Transaction signedTransaction = _createSignedTransaction(paymentAmountsWithChange, transactionOutputsToSpend, opReturnScript);
        if (signedTransaction == null) { return null; }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        Logger.log(signedTransaction.getHash());
        Logger.log(transactionDeflater.toBytes(signedTransaction));

        final Integer transactionByteCount = transactionDeflater.getByteCount(signedTransaction);

        if (feesContainer.value < (transactionByteCount * _satoshisPerByteFee)) {
            Logger.log("Failed to create a transaction with sufficient fee...");
            return null;
        }

        Logger.log("Transaction Bytes Count: " + transactionByteCount + " (" + (feesContainer.value / transactionByteCount.floatValue()) + " sats/byte)");

        return signedTransaction;
    }

    protected MutableBloomFilter _generateBloomFilter() {
        final AddressInflater addressInflater = new AddressInflater();

        final Collection<PrivateKey> privateKeys = _privateKeys.values();

        final long itemCount;
        {
            final int privateKeyCount = privateKeys.size();
            final int estimatedItemCount = (privateKeyCount * 4);
            itemCount = (int) (Math.pow(2, (BitcoinUtil.log2(estimatedItemCount) + 1)));
        }

        final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance(Math.max(itemCount, 1024), 0.0001D);

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

    public Wallet() {
        _medianBlockTime = null; // Only necessary for instances near impending hard forks...
    }

    public Wallet(final MedianBlockTime medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }

    public void setSatoshisPerByteFee(final Double satoshisPerByte) {
        _satoshisPerByteFee = satoshisPerByte;
    }

    public Long getDustThreshold(final Boolean addressIsCompressed) {
        return _calculateDustThreshold(BYTES_PER_TRANSACTION_OUTPUT, addressIsCompressed);
    }

    public synchronized void addPrivateKey(final PrivateKey privateKey) {
        _addPrivateKey(privateKey);
        _cachedBloomFilter = null; // Invalidate the cached BloomFilter...
        _reloadTransactions();
    }

    public synchronized void addPrivateKeys(final List<PrivateKey> privateKeys) {
        for (final PrivateKey privateKey : privateKeys) {
            _addPrivateKey(privateKey);
        }
        _cachedBloomFilter = null; // Invalidate the cached BloomFilter...
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
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, null);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final LockingScript opReturnScript) {
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, opReturnScript);
    }

    public Long calculateFees(final Integer newOutputCount, final Integer outputsBeingSpentCount) {
        final Container<Long> feeContainer = _createNewFeeContainer(newOutputCount, null);
        feeContainer.value += (long) ((BYTES_PER_TRANSACTION_INPUT * _satoshisPerByteFee) * outputsBeingSpentCount);
        return feeContainer.value;
    }

    public Long calculateFees(final Integer newOutputCount, final Integer outputsBeingSpentCount, final LockingScript opReturnScript) {
        final Container<Long> feeContainer = _createNewFeeContainer(newOutputCount, opReturnScript);
        feeContainer.value += (long) ((BYTES_PER_TRANSACTION_INPUT * _satoshisPerByteFee) * outputsBeingSpentCount);
        return feeContainer.value;
    }

    public synchronized Transaction createTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress) {
        final List<TransactionOutputIdentifier> mandatoryTransactionOutputsToSpend = new MutableList<TransactionOutputIdentifier>(0);
        return _createSignedTransaction(paymentAmounts, changeAddress, mandatoryTransactionOutputsToSpend, null);
    }

    public synchronized Transaction createTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress, final LockingScript opReturnScript) {
        final List<TransactionOutputIdentifier> mandatoryTransactionOutputsToSpend = new MutableList<TransactionOutputIdentifier>(0);
        return _createSignedTransaction(paymentAmounts, changeAddress, mandatoryTransactionOutputsToSpend, opReturnScript);
    }

    public synchronized Transaction createTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> transactionOutputIdentifiersToSpend) {
        return _createSignedTransaction(paymentAmounts, changeAddress, transactionOutputIdentifiersToSpend, null);
    }

    public synchronized Transaction createTransaction(final List<PaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> transactionOutputIdentifiersToSpend, final LockingScript opReturnScript) {
        return _createSignedTransaction(paymentAmounts, changeAddress, transactionOutputIdentifiersToSpend, opReturnScript);
    }

    public synchronized Transaction createSlpTokenTransaction(final SlpTokenId slpTokenId, final List<SlpPaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> requiredTransactionOutputIdentifiersToSpend) {
        final MutableSlpSendScript slpSendScript = new MutableSlpSendScript();
        final long requiredTokenAmount;
        { // Calculate the total token amount and build the SlpSendScript used to create the SLP LockingScript...
            long totalAmount = 0L;
            slpSendScript.setTokenId(slpTokenId);
            for (int i = 0; i < paymentAmounts.getSize(); ++i) {
                final SlpPaymentAmount slpPaymentAmount = paymentAmounts.get(i);
                final int transactionOutputId = (i + 1);
                slpSendScript.setAmount(transactionOutputId, slpPaymentAmount.tokenAmount);
                totalAmount += slpPaymentAmount.tokenAmount;
            }
            requiredTokenAmount = totalAmount;
        }

        final long preselectedTokenAmount;
        { // Calculate the total SLP Token amount selected by the required TransactionOutputs...
            long totalAmount = 0L;
            for (final TransactionOutputIdentifier transactionOutputIdentifier : requiredTransactionOutputIdentifiersToSpend) {
                // final SpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
                final Long transactionOutputTokenAmount = _getSlpTokenAmount(transactionOutputIdentifier);
                if (transactionOutputTokenAmount == null) { return null; }

                totalAmount += transactionOutputTokenAmount;
            }
            preselectedTokenAmount = totalAmount;
        }

        final MutableList<PaymentAmount> mutablePaymentAmounts = new MutableList<PaymentAmount>(paymentAmounts.getSize());
        for (final PaymentAmount paymentAmount : paymentAmounts) {
            mutablePaymentAmounts.add(paymentAmount);
        }

        // Add additional inputs to fulfill the requested payment amount(s)...
        final MutableList<TransactionOutputIdentifier> transactionOutputIdentifiersToSpend = new MutableList<TransactionOutputIdentifier>(requiredTransactionOutputIdentifiersToSpend);
        if (preselectedTokenAmount < requiredTokenAmount) {
            final MutableList<Tuple<TransactionOutputIdentifier, Long>> availableTokenAmounts = new MutableList<Tuple<TransactionOutputIdentifier, Long>>();
            for (final TransactionOutputIdentifier transactionOutputIdentifier : _transactionOutputs.keySet()) {
                final SpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
                if (spendableTransactionOutput.isSpent()) { continue; }

                if (requiredTransactionOutputIdentifiersToSpend.contains(transactionOutputIdentifier)) { continue; }

                if (! _isSlpTokenOutput(transactionOutputIdentifier)) { continue; }
                final Long tokenAmount = _getSlpTokenAmount(transactionOutputIdentifier);
                if (tokenAmount == null) { continue; }
                if (tokenAmount < 1L) { continue; }

                final Tuple<TransactionOutputIdentifier, Long> tokenAmountTuple = new Tuple<TransactionOutputIdentifier, Long>();
                tokenAmountTuple.first = transactionOutputIdentifier;
                tokenAmountTuple.second = tokenAmount;

                availableTokenAmounts.add(tokenAmountTuple);
            }
            availableTokenAmounts.sort(new Comparator<Tuple<TransactionOutputIdentifier, Long>>() {
                @Override
                public int compare(final Tuple<TransactionOutputIdentifier, Long> tuple0, final Tuple<TransactionOutputIdentifier, Long> tuple1) {
                    final Long amount0 = tuple0.second;
                    final Long amount1 = tuple1.second;
                    return amount0.compareTo(amount1);
                }
            });

            long selectedTokenAmount = preselectedTokenAmount;
            while (selectedTokenAmount < requiredTokenAmount) {
                final long missingAmount = (requiredTokenAmount - selectedTokenAmount);
                final Tuple<TransactionOutputIdentifier, Long> closestAmountTuple = Wallet.removeClosestTupleAmount(availableTokenAmounts, missingAmount);
                if (closestAmountTuple == null) { return null; } // Insufficient tokens to fulfill payment amount...

                if (closestAmountTuple.second >= (requiredTokenAmount - preselectedTokenAmount)) { // If the next output covers the whole transaction, only use itself and the required outputs...
                    transactionOutputIdentifiersToSpend.clear();
                    transactionOutputIdentifiersToSpend.addAll(requiredTransactionOutputIdentifiersToSpend);
                    transactionOutputIdentifiersToSpend.add(closestAmountTuple.first);
                    selectedTokenAmount = (preselectedTokenAmount + closestAmountTuple.second);
                    break;
                }

                selectedTokenAmount += closestAmountTuple.second;
                transactionOutputIdentifiersToSpend.add(closestAmountTuple.first);
            }

            // Direct excess tokens to the changeAddress...
            final long changeAmount = (selectedTokenAmount - requiredTokenAmount);
            if (changeAmount > 0L) {
                final int changeOutputIndex;
                {
                    Integer index = null;
                    // If the changeAddress is already specified, reuse its output's index...
                    for (int i = 0; i < paymentAmounts.getSize(); ++i) {
                        final PaymentAmount paymentAmount = paymentAmounts.get(i);
                        if (Util.areEqual(changeAddress, paymentAmount.address)) {
                            index = i;
                            break;
                        }
                    }
                    if (index != null) {
                        changeOutputIndex = index;
                    }
                    else {
                        // Add the change address as an output...
                        final Long bchAmount = _calculateDustThreshold(BYTES_PER_TRANSACTION_OUTPUT, changeAddress.isCompressed());
                        final SlpPaymentAmount changePaymentAmount = new SlpPaymentAmount(changeAddress, bchAmount, changeAmount);
                        mutablePaymentAmounts.add(changePaymentAmount);
                        changeOutputIndex = (mutablePaymentAmounts.getSize() - 1);
                    }
                }
                slpSendScript.setAmount((changeOutputIndex + 1), changeAmount); // The index is increased by one to account for the SlpScript TransactionOutput...
            }
        }

        final SlpScriptBuilder slpScriptBuilder = new SlpScriptBuilder();
        final LockingScript slpTokenScript = slpScriptBuilder.createSendScript(slpSendScript);
        return _createSignedTransaction(mutablePaymentAmounts, changeAddress, transactionOutputIdentifiersToSpend, slpTokenScript);
    }

    public synchronized MutableBloomFilter generateBloomFilter() {
        return _generateBloomFilter();
    }

    public synchronized BloomFilter getBloomFilter() {
        if (_cachedBloomFilter == null) {
            _cachedBloomFilter = _generateBloomFilter();
        }

        return _cachedBloomFilter;
    }

    public synchronized SpendableTransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionOutputs.get(transactionOutputIdentifier);
    }

    public synchronized List<SpendableTransactionOutput> getTransactionOutputs() {
        final Collection<? extends SpendableTransactionOutput> spendableTransactionOutputs = _transactionOutputs.values();
        final ImmutableListBuilder<SpendableTransactionOutput> transactionOutputs = new ImmutableListBuilder<SpendableTransactionOutput>(spendableTransactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            transactionOutputs.add(spendableTransactionOutput);
        }
        return transactionOutputs.build();
    }

    public synchronized List<SpendableTransactionOutput> getNonSlpTokenTransactionOutputs() {
        final Collection<? extends SpendableTransactionOutput> spendableTransactionOutputs = _transactionOutputs.values();
        final ImmutableListBuilder<SpendableTransactionOutput> transactionOutputs = new ImmutableListBuilder<SpendableTransactionOutput>(spendableTransactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
            if (! _isSlpTokenOutput(transactionOutputIdentifier)) {
                transactionOutputs.add(spendableTransactionOutput);
            }
        }
        return transactionOutputs.build();
    }

    public synchronized List<SpendableTransactionOutput> getTransactionOutputsAndSpendableTokens(final SlpTokenId tokenId) {
        final Collection<? extends SpendableTransactionOutput> spendableTransactionOutputs = _transactionOutputs.values();
        final ImmutableListBuilder<SpendableTransactionOutput> transactionOutputs = new ImmutableListBuilder<SpendableTransactionOutput>(spendableTransactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
            if (! _isSlpTokenOutput(transactionOutputIdentifier)) {
                transactionOutputs.add(spendableTransactionOutput);
            }
            else if (_outputContainsSpendableSlpTokens(transactionOutputIdentifier)) {
                if (Util.areEqual(tokenId, _getSlpTokenId(transactionOutputIdentifier))) {
                    transactionOutputs.add(spendableTransactionOutput);
                }
            }
        }
        return transactionOutputs.build();
    }

    public synchronized List<SlpToken> getSlpTokens() {
        return _getSlpTokens(null);
    }

    public synchronized List<SlpToken> getSlpTokens(final SlpTokenId slpTokenId) {
        return _getSlpTokens(slpTokenId);
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

    public synchronized Long getBalance(final PublicKey publicKey) {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final AddressInflater addressInflater = new AddressInflater();
        final Address compressedAddress = addressInflater.compressedFromPublicKey(publicKey);
        final Address address = addressInflater.fromPublicKey(publicKey);

        long amount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            if (! spendableTransactionOutput.isSpent()) {
                final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                final Address outputAddress = scriptPatternMatcher.extractAddress(scriptType, lockingScript);

                if ( Util.areEqual(address, outputAddress) || Util.areEqual(compressedAddress, outputAddress) ) {
                    amount += transactionOutput.getAmount();
                }
            }
        }
        return amount;
    }

    public synchronized Long getSlpTokenBalance(final SlpTokenId tokenId) {
        long amount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            if (! spendableTransactionOutput.isSpent()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

                final Transaction transaction = _transactions.get(transactionHash);
                if (transaction == null) { return null; }

                final boolean outputContainsTokens = SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
                if (! outputContainsTokens) { continue; }

                final SlpTokenId transactionTokenId = SlpUtil.getTokenId(transaction);
                if (! Util.areEqual(tokenId, transactionTokenId)) { continue; }

                amount += SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
            }
        }
        return amount;
    }

    public synchronized List<SlpTokenId> getSlpTokenIds() {
        final HashSet<SlpTokenId> tokenIdsSet = new HashSet<SlpTokenId>();
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            if (! spendableTransactionOutput.isSpent()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

                final Transaction transaction = _transactions.get(transactionHash);
                if (transaction == null) { return null; }

                final boolean outputContainsTokens = SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
                if (! outputContainsTokens) { continue; }

                final SlpTokenId tokenId = SlpUtil.getTokenId(transaction);
                if (tokenId == null) { continue; }

                tokenIdsSet.add(tokenId);
            }
        }

        final MutableList<SlpTokenId> tokenIds = new MutableList<SlpTokenId>();
        tokenIds.addAll(tokenIdsSet);
        tokenIds.sort(SlpTokenId.COMPARATOR);
        return tokenIds;
    }

    public synchronized Boolean hasPrivateKeys() {
        return (! _privateKeys.isEmpty());
    }

    public synchronized Address getReceivingAddress() {
        final AddressInflater addressInflater = new AddressInflater();
        for (final PublicKey publicKey : _privateKeys.keySet()) {
            return addressInflater.compressedFromPublicKey(publicKey);
        }

        return null;
    }

    public synchronized List<PublicKey> getPublicKeys() {
        return new ImmutableList<PublicKey>(_privateKeys.keySet());
    }
}
