package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SlpValidity;
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
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
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
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class Wallet {
    protected static final Long BYTES_PER_TRANSACTION_INPUT = 148L; // P2PKH Inputs are either 147-148 bytes for compressed addresses, or 179-180 bytes for uncompressed addresses.
    protected static final Long BYTES_PER_UNCOMPRESSED_TRANSACTION_INPUT = 180L;
    protected static final Long BYTES_PER_TRANSACTION_OUTPUT = 34L;
    protected static final Long BYTES_PER_TRANSACTION_HEADER = 10L; // This value becomes inaccurate if either the number of inputs or the number out outputs exceeds 252 (The max value of a 1-byte variable length integer)...

    public static Long getDefaultDustThreshold() {
        return (long) ((BYTES_PER_TRANSACTION_OUTPUT + BYTES_PER_TRANSACTION_INPUT) * 3D);
    }

    protected static class SlpTokenTransactionConfiguration {
        public final MutableList<PaymentAmount> mutablePaymentAmounts = new MutableList<PaymentAmount>();
        public final MutableList<TransactionOutputIdentifier> transactionOutputIdentifiersToSpend = new MutableList<TransactionOutputIdentifier>();
        public final MutableSlpSendScript slpSendScript = new MutableSlpSendScript();
    }

    protected static final Address DUMMY_ADDRESS = (new AddressInflater()).fromBytes(new MutableByteArray(Address.BYTE_COUNT));

    protected final UpgradeSchedule _upgradeSchedule;
    protected final HashMap<Address, PublicKey> _publicKeys = new HashMap<Address, PublicKey>();
    protected final HashMap<PublicKey, PrivateKey> _privateKeys = new HashMap<PublicKey, PrivateKey>();
    protected final HashMap<Sha256Hash, Transaction> _transactions = new HashMap<Sha256Hash, Transaction>();
    protected final HashSet<Sha256Hash> _confirmedTransactions = new HashSet<Sha256Hash>();
    protected final HashSet<Sha256Hash> _notYetValidatedSlpTransactions = new HashSet<Sha256Hash>();
    protected final HashSet<Sha256Hash> _invalidSlpTransactions = new HashSet<Sha256Hash>();
    protected final HashSet<Sha256Hash> _validSlpTransactions = new HashSet<Sha256Hash>();

    protected final HashMap<TransactionOutputIdentifier, Sha256Hash> _spentTransactionOutputs = new HashMap<TransactionOutputIdentifier, Sha256Hash>();
    protected final HashMap<TransactionOutputIdentifier, MutableSpendableTransactionOutput> _transactionOutputs = new HashMap<TransactionOutputIdentifier, MutableSpendableTransactionOutput>();
    protected final Map<TransactionOutputIdentifier, Sha256Hash> _externallySpentTransactionOutputs = new HashMap<>();
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

    protected void _debugWalletState() {
        Logger.debug("Wallet Transaction Hashes:");
        for (final Sha256Hash transactionHash : _transactions.keySet()) {
            Logger.debug(transactionHash);
        }
        Logger.debug("Wallet Available Outputs:");
        for (final TransactionOutputIdentifier transactionOutputIdentifier : _transactionOutputs.keySet()) {
            Logger.debug(transactionOutputIdentifier);
        }
        Logger.debug("Wallet Public Keys:");
        for (final PublicKey publicKey : _privateKeys.keySet()) {
            Logger.debug(publicKey);
        }
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

        final boolean isPartOfValidSlpTransaction = _isSlpTransactionAndIsValid(transactionHash, true);
        return (isPartOfValidSlpTransaction && SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex));
    }

    protected Boolean _outputContainsSpendableSlpTokens(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

        final Transaction transaction = _transactions.get(transactionHash);
        if (transaction == null) {
            throw new RuntimeException("Unable to find TransactionOutput: " + transactionOutputIdentifier);
        }

        final boolean isPartOfValidSlpTransaction = _isSlpTransactionAndIsValid(transactionHash, true);
        return (isPartOfValidSlpTransaction && SlpUtil.outputContainsSpendableSlpTokens(transaction, transactionOutputIndex));
    }

    protected List<SlpToken> _getSlpTokens(final SlpTokenId matchingSlpTokenId, final Boolean shouldIncludeNotYetValidatedTransactions) {
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

            if (! _isSlpTransactionAndIsValid(transactionHash, shouldIncludeNotYetValidatedTransactions)) { continue; }

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
        final Address decompressedAddress = addressInflater.fromPrivateKey(constPrivateKey, false);
        final Address compressedAddress = addressInflater.fromPrivateKey(constPrivateKey, true);

        _privateKeys.put(compressedPublicKey.asConst(), constPrivateKey);
        _privateKeys.put(decompressedPublicKey.asConst(), constPrivateKey);

        _publicKeys.put(compressedAddress, compressedPublicKey);
        _publicKeys.put(decompressedAddress, decompressedPublicKey);
    }

    protected Boolean _hasSpentInputs(final Transaction transaction) {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final boolean inputWasSpent = _spentTransactionOutputs.containsKey(transactionOutputIdentifier);
            if (inputWasSpent) {
                return true;
            }
        }

        return false;
    }

    protected void _addTransaction(final Transaction transaction, final Boolean isConfirmedTransaction) {
        final Transaction constTransaction = transaction.asConst();
        final Sha256Hash transactionHash = constTransaction.getHash();

        if ( (! isConfirmedTransaction) && _hasSpentInputs(transaction) ) {
            Logger.debug("Wallet is not adding already spent unconfirmed transaction: " + transactionHash);
            return;
        }

        _transactions.put(transactionHash, constTransaction);
        if (isConfirmedTransaction) {
            _confirmedTransactions.add(transactionHash);
        }

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
        for (int transactionOutputIndex = 0; transactionOutputIndex < transactionOutputs.getCount(); ++transactionOutputIndex) {
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

        if (Transaction.isSlpTransaction(transaction)) {
            // check for validity and stop tracking its validity explicitly if it is valid
            if (! _validSlpTransactions.remove(transactionHash)) {
                // not known to be valid
                if (! _invalidSlpTransactions.contains(transactionHash)) {
                    // not known to be invalid either, add as not yet validated
                    _notYetValidatedSlpTransactions.add(transactionHash);
                }
            }
        }
    }

    protected void _reloadTransactions() {
        final MutableList<Transaction> transactions = new MutableList<Transaction>(_transactions.values());
        final HashSet<Sha256Hash> confirmedTransactions = new HashSet<Sha256Hash>(_confirmedTransactions);
        final Map<TransactionOutputIdentifier, Sha256Hash> externallySpentTransactionOutputs = new HashMap<>(_externallySpentTransactionOutputs);

        _externallySpentTransactionOutputs.clear();
        _spentTransactionOutputs.clear();
        _transactionOutputs.clear();
        _transactions.clear();
        _confirmedTransactions.clear();

        // intentionally not clearing SLP sets, since their state should remain valid across the reload

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final boolean isConfirmedTransaction = confirmedTransactions.contains(transactionHash);

            // add any previously processed, valid SLP transactions (back) to the valid SLP transactions set
            if (Transaction.isSlpTransaction(transaction)) {
                if ( (! _notYetValidatedSlpTransactions.contains(transactionHash)) && (! _invalidSlpTransactions.contains(transactionHash)) ) {
                    _validSlpTransactions.add(transactionHash);
                }
            }

            _addTransaction(transaction, isConfirmedTransaction);
        }

        // transfer explicitly spent outputs to new data set
        for (final TransactionOutputIdentifier transactionOutputIdentifier : externallySpentTransactionOutputs.keySet()) {
            if (_transactions.containsKey(transactionOutputIdentifier.getTransactionHash())) {
                _markTransactionOutputAsSpent(transactionOutputIdentifier);
            }
        }
    }

    /**
     * Return true if the transaction with the provided hash is known to be a valid SLP transaction or if <code>shouldTreatUnknownTransactionsAsValid</code>
     * is true and the transaction is known to be an SLP transaction but is not known to be valid or not.
     *
     * If the transaction is completely unknown, returns null.
     *
     * Otherwise, returns false.
     * @param transactionHash
     * @param shouldTreatUnknownTransactionsAsValid
     * @return
     */
    protected Boolean _isSlpTransactionAndIsValid(final Sha256Hash transactionHash, final Boolean shouldTreatUnknownTransactionsAsValid) {
        // if we know a transaction is valid, return true whether we have it or not
        if (_validSlpTransactions.contains(transactionHash)) {
            return true;
        }

        // if the transaction is not known, we cannot make a decision
        if (! _transactions.containsKey(transactionHash)) {
            return null;
        }

        final Transaction transaction = _transactions.get(transactionHash);
        if (! Transaction.isSlpTransaction(transaction)) {
            return false;
        }

        if (_invalidSlpTransactions.contains(transactionHash)) {
            return false;
        }

        if (_notYetValidatedSlpTransactions.contains(transactionHash)) {
            return shouldTreatUnknownTransactionsAsValid;
        }

        // is an SLP transaction and is not invalid or unknown so it must be valid
        return true;
    }

    protected Long _getBalance(final PublicKey publicKey, final SlpTokenId slpTokenId, final Boolean shouldIncludeNotYetValidatedTransactions) {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final AddressInflater addressInflater = new AddressInflater();
        final Address address = addressInflater.fromPublicKey(publicKey, false);
        final Address compressedAddress = addressInflater.fromPublicKey(publicKey, true);

        long amount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            if (! spendableTransactionOutput.isSpent()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();

                final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                final Address outputAddress = scriptPatternMatcher.extractAddress(scriptType, lockingScript);

                if ( (! Util.areEqual(address, outputAddress)) && (! Util.areEqual(compressedAddress, outputAddress)) ) { continue; }

                if (slpTokenId == null) { // If the slpTokenId is null then only sum its BCH value.
                    amount += transactionOutput.getAmount();
                }
                else {
                    // If the slpTokenId is provided but does not match the Transaction's SlpTokenId ignore the amount.
                    // Otherwise, ensure retrieve its token balance.

                    final SlpTokenId transactionTokenId = _getSlpTokenId(transactionOutputIdentifier);
                    if (! Util.areEqual(slpTokenId, transactionTokenId)) { continue; }
                    final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                    if (! _isSlpTransactionAndIsValid(transactionHash, shouldIncludeNotYetValidatedTransactions)) { continue; }

                    final Long slpTokenAmount = _getSlpTokenAmount(transactionOutputIdentifier);
                    amount += slpTokenAmount;
                }
            }
        }
        return amount;
    }

    protected Long _getSlpTokenBalance(final SlpTokenId tokenId, final Boolean shouldIncludeNotYetValidatedTransactions) {
        long amount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            if (! spendableTransactionOutput.isSpent()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

                if (_isSlpTransactionAndIsValid(transactionHash, shouldIncludeNotYetValidatedTransactions)) {
                    final Transaction transaction = _transactions.get(transactionHash);
                    if (transaction == null) { return null; }

                    final boolean outputContainsTokens = SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
                    if (! outputContainsTokens) { continue; }

                    final SlpTokenId transactionTokenId = SlpUtil.getTokenId(transaction);
                    if (! Util.areEqual(tokenId, transactionTokenId)) { continue; }

                    amount += SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
                }
            }
        }
        return amount;
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
            if ( (mandatoryTransactionOutputsToSpend != null) && (mandatoryTransactionOutputsToSpend.contains(transactionOutputIdentifier)) ) {
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

        final long mandatoryOutputsFundingAmount = selectedUtxoAmount;

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
                    if ( (mandatoryTransactionOutputsToSpend == null) || (! mandatoryTransactionOutputsToSpend.contains(transactionOutputIdentifier)) ) {
                        mutableIterator.remove();

                        // Subtract the fee for spending this output...
                        final Address addressBeingRemoved = selectedTransactionOutput.getAddress();
                        final Long feeToSpendRemovedOutput = (addressBeingRemoved.isCompressed() ? feeToSpendOneOutput : feeToSpendOneUncompressedOutput);
                        feesToSpendOutputs.value -= feeToSpendRemovedOutput;
                    }
                }

                feesToSpendOutputs.value += feeToSpendThisOutput;
                selectedUtxoAmount = transactionOutputAmount + mandatoryOutputsFundingAmount;
                transactionOutputsToSpend.add(spendableTransactionOutput);

                break;
            }

            feesToSpendOutputs.value += feeToSpendThisOutput;
            selectedUtxoAmount += transactionOutputAmount;
            transactionOutputsToSpend.add(spendableTransactionOutput);
        }

        if (selectedUtxoAmount < (minimumUtxoAmount + feesToSpendOutputs.value)) {
            Logger.info("Insufficient funds to fund transaction.");
            if (Logger.isDebugEnabled()) {
                _debugWalletState();
            }
            feesToSpendOutputs.value = originalFeesToSpendOutputs; // Reset the feesToSpendOutputs container...
            return null;
        }

        return transactionOutputsToSpend;
    }

    protected List<TransactionOutputIdentifier> _getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final SlpTokenId slpTokenId, final Long desiredSlpSpendAmount, final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend, final Boolean shouldIncludeNotYetValidatedTransactions) {
        final MutableList<SlpPaymentAmount> slpPaymentAmounts = new MutableList<SlpPaymentAmount>(newTransactionOutputCount);
        { // Create fake SlpPaymentAmounts that sum exactly to the requested amounts, and contain exactly newTransactionOutputCount items...
            if (newTransactionOutputCount > 0) {
                final Long fakeDesiredSpendAmount = (desiredSpendAmount - (newTransactionOutputCount - 1));
                final Long fakeDesiredSlpSpendAmount = (desiredSlpSpendAmount - (newTransactionOutputCount - 1));
                slpPaymentAmounts.add(new SlpPaymentAmount(DUMMY_ADDRESS, fakeDesiredSpendAmount, fakeDesiredSlpSpendAmount));
            }

            for (int i = 1; i < newTransactionOutputCount; ++i) {
                slpPaymentAmounts.add(new SlpPaymentAmount(DUMMY_ADDRESS, 1L, 1L));
            }
        }

        final SlpTokenTransactionConfiguration slpTokenTransactionConfiguration = _createSlpTokenTransactionConfiguration(slpTokenId, slpPaymentAmounts, DUMMY_ADDRESS, requiredTransactionOutputsToSpend, shouldIncludeNotYetValidatedTransactions);
        if (slpTokenTransactionConfiguration == null) { return null; }

        final SlpScriptBuilder slpScriptBuilder = new SlpScriptBuilder();
        final LockingScript slpTokenScript = slpScriptBuilder.createSendScript(slpTokenTransactionConfiguration.slpSendScript);

        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, slpTokenScript, slpTokenTransactionConfiguration.transactionOutputIdentifiersToSpend);
    }

    protected List<TransactionOutputIdentifier> _getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final LockingScript opReturnScript, final List<TransactionOutputIdentifier> requiredTransactionOutputIdentifiersToSpend) {
        final Container<Long> feesContainer = _createNewFeeContainer(newTransactionOutputCount, opReturnScript);
        final List<SpendableTransactionOutput> spendableTransactionOutputs = _getOutputsToSpend(desiredSpendAmount, feesContainer, requiredTransactionOutputIdentifiersToSpend);
        if (spendableTransactionOutputs == null) { return null; }

        final MutableList<TransactionOutputIdentifier> transactionOutputs = new MutableList<TransactionOutputIdentifier>(spendableTransactionOutputs.getCount());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            transactionOutputs.add(spendableTransactionOutput.getIdentifier());
        }
        return transactionOutputs;
    }

    protected SlpTokenTransactionConfiguration _createSlpTokenTransactionConfiguration(final SlpTokenId slpTokenId, final List<SlpPaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> requiredTransactionOutputIdentifiersToSpend, final Boolean shouldIncludeNotYetValidatedTransactions) {
        final SlpTokenTransactionConfiguration configuration = new SlpTokenTransactionConfiguration();

        final long requiredTokenAmount;
        { // Calculate the total token amount and build the SlpSendScript used to create the SLP LockingScript...
            long totalAmount = 0L;
            configuration.slpSendScript.setTokenId(slpTokenId);
            for (int i = 0; i < paymentAmounts.getCount(); ++i) {
                final SlpPaymentAmount slpPaymentAmount = paymentAmounts.get(i);
                final int transactionOutputId = (i + 1);
                configuration.slpSendScript.setAmount(transactionOutputId, slpPaymentAmount.tokenAmount);
                totalAmount += slpPaymentAmount.tokenAmount;
            }
            requiredTokenAmount = totalAmount;
        }

        final long preselectedTokenAmount;
        { // Calculate the total SLP Token amount selected by the required TransactionOutputs...
            long totalAmount = 0L;
            for (final TransactionOutputIdentifier transactionOutputIdentifier : requiredTransactionOutputIdentifiersToSpend) {
                final SlpTokenId outputTokenId = _getSlpTokenId(transactionOutputIdentifier);
                final Boolean isValidSlpTransaction = _isSlpTransactionAndIsValid(transactionOutputIdentifier.getTransactionHash(), shouldIncludeNotYetValidatedTransactions);
                if (isValidSlpTransaction == null) {
                    Logger.error("Unable to add required output " + transactionOutputIdentifier.getTransactionHash() + ":" + transactionOutputIdentifier.getOutputIndex() + " as the transaction is not available.");
                    return null;
                }
                final Boolean isSlpOutput = _isSlpTokenOutput(transactionOutputIdentifier);
                if (isValidSlpTransaction && isSlpOutput) {
                    if (! Util.areEqual(slpTokenId, outputTokenId)) {
                        Logger.warn("Required output " + transactionOutputIdentifier.getTransactionHash() + ":" + transactionOutputIdentifier.getOutputIndex() + " has a different token type (" + outputTokenId + ") than the transaction being created (" + slpTokenId + ").  These tokens will be burned.");
                    }

                    final Long transactionOutputTokenAmount = _getSlpTokenAmount(transactionOutputIdentifier);
                    totalAmount += transactionOutputTokenAmount;
                }

                configuration.transactionOutputIdentifiersToSpend.add(transactionOutputIdentifier);
            }
            preselectedTokenAmount = totalAmount;
        }

        for (final PaymentAmount paymentAmount : paymentAmounts) {
            configuration.mutablePaymentAmounts.add(paymentAmount);
        }

        // Add additional inputs to fulfill the requested payment amount(s)...
        long selectedTokenAmount = preselectedTokenAmount;
        if (selectedTokenAmount < requiredTokenAmount) {
            final MutableList<Tuple<TransactionOutputIdentifier, Long>> availableTokenAmounts = new MutableList<Tuple<TransactionOutputIdentifier, Long>>();
            for (final TransactionOutputIdentifier transactionOutputIdentifier : _transactionOutputs.keySet()) {
                final SpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
                if (spendableTransactionOutput.isSpent()) { continue; }

                if (requiredTransactionOutputIdentifiersToSpend.contains(transactionOutputIdentifier)) { continue; }

                if (! _isSlpTokenOutput(transactionOutputIdentifier)) { continue; }
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                if (! _isSlpTransactionAndIsValid(transactionHash, shouldIncludeNotYetValidatedTransactions)) { continue; }

                final SlpTokenId outputTokenId = _getSlpTokenId(transactionOutputIdentifier);
                if (! Util.areEqual(slpTokenId, outputTokenId)) { continue; }

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

            while (selectedTokenAmount < requiredTokenAmount) {
                final long missingAmount = (requiredTokenAmount - selectedTokenAmount);
                final Tuple<TransactionOutputIdentifier, Long> closestAmountTuple = Wallet.removeClosestTupleAmount(availableTokenAmounts, missingAmount);
                if (closestAmountTuple == null) {
                    Logger.info("Insufficient tokens to fulfill payment amount. Required: " + requiredTokenAmount + " Available: " + selectedTokenAmount);
                    return null;
                }

                if (closestAmountTuple.second >= (requiredTokenAmount - preselectedTokenAmount)) { // If the next output covers the whole transaction, only use itself and the required outputs...
                    configuration.transactionOutputIdentifiersToSpend.clear();
                    configuration.transactionOutputIdentifiersToSpend.addAll(requiredTransactionOutputIdentifiersToSpend);
                    configuration.transactionOutputIdentifiersToSpend.add(closestAmountTuple.first);
                    selectedTokenAmount = (preselectedTokenAmount + closestAmountTuple.second);
                    break;
                }

                selectedTokenAmount += closestAmountTuple.second;
                configuration.transactionOutputIdentifiersToSpend.add(closestAmountTuple.first);
            }
        }

        // Direct excess tokens to the changeAddress...
        final long changeAmount = (selectedTokenAmount - requiredTokenAmount);
        if (changeAmount > 0L) {
            final int changeOutputIndex;
            {
                Integer index = null;
                // If the changeAddress is already specified, reuse its output's index...
                for (int i = 0; i < paymentAmounts.getCount(); ++i) {
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
                    configuration.mutablePaymentAmounts.add(changePaymentAmount);
                    changeOutputIndex = (configuration.mutablePaymentAmounts.getCount() - 1);
                }
            }
            configuration.slpSendScript.setAmount((changeOutputIndex + 1), changeAmount); // The index is increased by one to account for the SlpScript TransactionOutput...
        }

        return configuration;
    }

    protected Transaction _createSignedTransaction(final List<PaymentAmount> paymentAmounts, final List<SpendableTransactionOutput> transactionOutputsToSpend, final LockingScript opReturnScript) {
        if ( paymentAmounts.isEmpty() && (opReturnScript == null) ) { return null; }
        if (transactionOutputsToSpend == null) { return null; }

        final MutableTransaction transaction = new MutableTransaction();
        transaction.setVersion(Transaction.VERSION);
        transaction.setLockTime(LockTime.MIN_TIMESTAMP);

        for (int i = 0; i < transactionOutputsToSpend.getCount(); ++i) {
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

        for (int i = 0; i < paymentAmounts.getCount(); ++i) {
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
            for (int i = 0; i < transactionInputs.getCount(); ++i) {
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

                final SignatureContext signatureContext = new SignatureContext(transactionBeingSigned, new HashType(Mode.SIGNATURE_HASH_ALL, true, true), _upgradeSchedule);
                signatureContext.setInputIndexBeingSigned(i);
                signatureContext.setShouldSignInputScript(i, true, transactionOutputBeingSpent);
                transactionBeingSigned = transactionSigner.signTransaction(signatureContext, privateKey, useCompressedPublicKey);
            }

            signedTransaction = transactionBeingSigned;
        }

        final ScriptRunner scriptRunner = new ScriptRunner(_upgradeSchedule);
        final List<TransactionInput> signedTransactionInputs = signedTransaction.getTransactionInputs();
        for (int i = 0; i < signedTransactionInputs.getCount(); ++i) {
            final TransactionInput signedTransactionInput = signedTransactionInputs.get(i);
            final TransactionOutput transactionOutputBeingSpent;
            {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(signedTransactionInput.getPreviousOutputTransactionHash(), signedTransactionInput.getPreviousOutputIndex());
                final SpendableTransactionOutput spendableTransactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
                transactionOutputBeingSpent = spendableTransactionOutput.getTransactionOutput();
            }

            final MutableTransactionContext context = MutableTransactionContext.getContextForVerification(signedTransaction, i, transactionOutputBeingSpent, _medianBlockTime, _upgradeSchedule);
            final Boolean outputIsUnlocked = scriptRunner.runScript(transactionOutputBeingSpent.getLockingScript(), signedTransactionInput.getUnlockingScript(), context);

            if (! outputIsUnlocked) {
                Logger.warn("Error signing transaction.");
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

        final int newOutputCount = (paymentAmounts.getCount() + (changeAddress != null ? 1 : 0));

        final Container<Long> feesContainer = _createNewFeeContainer(newOutputCount, opReturnScript);
        final List<SpendableTransactionOutput> transactionOutputsToSpend = _getOutputsToSpend(totalPaymentAmount, feesContainer, mandatoryOutputs);
        if (transactionOutputsToSpend == null) { return null; }

        long totalAmountSelected = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : transactionOutputsToSpend) {
            final TransactionOutput transactionOutput = spendableTransactionOutput.getTransactionOutput();
            totalAmountSelected += transactionOutput.getAmount();
        }

        final boolean shouldIncludeChangeOutput;
        final MutableList<PaymentAmount> paymentAmountsWithChange = new MutableList<PaymentAmount>(paymentAmounts.getCount() + 1);
        {
            paymentAmountsWithChange.addAll(paymentAmounts);

            final Long changeAmount = (totalAmountSelected - totalPaymentAmount - feesContainer.value);
            if (changeAddress != null) {
                final Long dustThreshold = _calculateDustThreshold(BYTES_PER_TRANSACTION_OUTPUT, changeAddress.isCompressed());
                shouldIncludeChangeOutput = (changeAmount >= dustThreshold);
            }
            else {
                shouldIncludeChangeOutput = false;
            }

            if (shouldIncludeChangeOutput) {
                // Check paymentAmountsWithChange for an existing output using the change address...
                Integer changePaymentAmountIndex = null;
                for (int i = 0; i < paymentAmountsWithChange.getCount(); ++i) {
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

        Logger.info("Creating Transaction. Spending " + transactionOutputsToSpend.getCount() + " UTXOs. Creating " + paymentAmountsWithChange.getCount() + " UTXOs. Sending " + totalPaymentAmount + ". Spending " + feesContainer.value + " in fees. " + (shouldIncludeChangeOutput ? ((totalAmountSelected - totalPaymentAmount - feesContainer.value) + " in change.") : ""));

        final Transaction signedTransaction = _createSignedTransaction(paymentAmountsWithChange, transactionOutputsToSpend, opReturnScript);
        if (signedTransaction == null) { return null; }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        Logger.debug(signedTransaction.getHash());
        Logger.debug(transactionDeflater.toBytes(signedTransaction));

        final Integer transactionByteCount = signedTransaction.getByteCount();

        if (feesContainer.value < (transactionByteCount * _satoshisPerByteFee)) {
            Logger.info("Failed to create a transaction with sufficient fee...");
            return null;
        }

        Logger.debug("Transaction Bytes Count: " + transactionByteCount + " (" + (feesContainer.value / transactionByteCount.floatValue()) + " sats/byte)");

        return signedTransaction;
    }

    protected Transaction _createSlpTokenTransaction(final SlpTokenId slpTokenId, final List<SlpPaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> requiredTransactionOutputIdentifiersToSpend, final Boolean shouldIncludeNotYetValidatedTransactions) {
        final SlpTokenTransactionConfiguration slpTokenTransactionConfiguration = _createSlpTokenTransactionConfiguration(slpTokenId, paymentAmounts, changeAddress, requiredTransactionOutputIdentifiersToSpend, shouldIncludeNotYetValidatedTransactions);
        if (slpTokenTransactionConfiguration == null) { return null; }

        final SlpScriptBuilder slpScriptBuilder = new SlpScriptBuilder();
        final LockingScript slpTokenScript = slpScriptBuilder.createSendScript(slpTokenTransactionConfiguration.slpSendScript);
        return _createSignedTransaction(slpTokenTransactionConfiguration.mutablePaymentAmounts, changeAddress, slpTokenTransactionConfiguration.transactionOutputIdentifiersToSpend, slpTokenScript);
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
            bloomFilter.addItem(addressInflater.fromPrivateKey(privateKey, false));
            bloomFilter.addItem(addressInflater.fromPrivateKey(privateKey, true));
        }

        return bloomFilter;
    }

    public Wallet() {
        _medianBlockTime = null; // Only necessary for instances near impending hard forks...
        _upgradeSchedule = new CoreUpgradeSchedule();
    }

    public Wallet(final MedianBlockTime medianBlockTime, final UpgradeSchedule upgradeSchedule) {
        _medianBlockTime = medianBlockTime;
        _upgradeSchedule = upgradeSchedule;
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
        _addTransaction(transaction, true);
    }

    public synchronized void addTransaction(final Transaction transaction, final List<Integer> validOutputIndexes) {
        _addTransaction(transaction, true);
        _markTransactionOutputsAsSpent(transaction, validOutputIndexes);
    }

    public synchronized void addUnconfirmedTransaction(final Transaction transaction) {
        _addTransaction(transaction, false);
    }

    public synchronized void addUnconfirmedTransaction(final Transaction transaction, final List<Integer> validOutputIndexes) {
        _addTransaction(transaction, false);
        _markTransactionOutputsAsSpent(transaction, validOutputIndexes);
    }

    public synchronized void markTransactionOutputAsSpent(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
        _markTransactionOutputAsSpent(transactionOutputIdentifier);
    }

    protected void _markTransactionOutputsAsSpent(final Transaction transaction, final List<Integer> validOutputIndexes) {
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            if (! validOutputIndexes.contains(transactionOutput.getIndex())) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), transactionOutput.getIndex());
                _markTransactionOutputAsSpent(transactionOutputIdentifier);
            }
        }
    }

    protected void _markTransactionOutputAsSpent(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final MutableSpendableTransactionOutput transactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
        if (transactionOutput != null) {
            transactionOutput.setIsSpent(true);
        }

        final Sha256Hash sentinelHash = Sha256Hash.EMPTY_HASH;
        _spentTransactionOutputs.put(transactionOutputIdentifier, sentinelHash);
        _externallySpentTransactionOutputs.put(transactionOutputIdentifier, sentinelHash);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount) {
        final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend = new MutableList<TransactionOutputIdentifier>(0);
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, null, requiredTransactionOutputsToSpend);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend) {
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, null, requiredTransactionOutputsToSpend);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final LockingScript opReturnScript) {
        final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend = new MutableList<TransactionOutputIdentifier>(0);
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, opReturnScript, requiredTransactionOutputsToSpend);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final SlpTokenId slpTokenId, final Long desiredSlpSpendAmount) {
        final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend = new MutableList<TransactionOutputIdentifier>(0);
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, slpTokenId, desiredSlpSpendAmount, requiredTransactionOutputsToSpend, true);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final SlpTokenId slpTokenId, final Long desiredSlpSpendAmount, final Boolean shouldIncludeNotYetValidatedTransactions) {
        final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend = new MutableList<TransactionOutputIdentifier>(0);
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, slpTokenId, desiredSlpSpendAmount, requiredTransactionOutputsToSpend, shouldIncludeNotYetValidatedTransactions);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final SlpTokenId slpTokenId, final Long desiredSlpSpendAmount, final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend) {
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, slpTokenId, desiredSlpSpendAmount, requiredTransactionOutputsToSpend, true);
    }

    public synchronized List<TransactionOutputIdentifier> getOutputsToSpend(final Integer newTransactionOutputCount, final Long desiredSpendAmount, final SlpTokenId slpTokenId, final Long desiredSlpSpendAmount, final List<TransactionOutputIdentifier> requiredTransactionOutputsToSpend, final Boolean shouldIncludeNotYetValidatedTransactions) {
        return _getOutputsToSpend(newTransactionOutputCount, desiredSpendAmount, slpTokenId, desiredSlpSpendAmount, requiredTransactionOutputsToSpend, shouldIncludeNotYetValidatedTransactions);
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

    public synchronized Transaction createSlpTokenTransaction(final SlpTokenId slpTokenId, final List<SlpPaymentAmount> paymentAmounts, final Address changeAddress) {
        return _createSlpTokenTransaction(slpTokenId, paymentAmounts, changeAddress, new MutableList<TransactionOutputIdentifier>(0), true);
    }

    public synchronized Transaction createSlpTokenTransaction(final SlpTokenId slpTokenId, final List<SlpPaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> requiredTransactionOutputIdentifiersToSpend) {
        return _createSlpTokenTransaction(slpTokenId, paymentAmounts, changeAddress, requiredTransactionOutputIdentifiersToSpend, true);
    }

    public synchronized Transaction createSlpTokenTransaction(final SlpTokenId slpTokenId, final List<SlpPaymentAmount> paymentAmounts, final Address changeAddress, final List<TransactionOutputIdentifier> requiredTransactionOutputIdentifiersToSpend, final Boolean shouldIncludeNotYetValidatedTransactions) {
        return _createSlpTokenTransaction(slpTokenId, paymentAmounts, changeAddress, requiredTransactionOutputIdentifiersToSpend, shouldIncludeNotYetValidatedTransactions);
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

    public synchronized Boolean hasTransaction(final Sha256Hash transactionHash) {
        return _transactions.containsKey(transactionHash);
    }

    /**
     * <p>Get a list of the transactions contained within this wallet object.</p>
     *
     * @return A new list populated with the transactions contained within the wallet.
     */
    public List<Transaction> getTransactions() {
        return new MutableList<>(_transactions.values());
    }

    public synchronized Transaction getTransaction(final Sha256Hash transactionHash) {
        final Transaction transaction = _transactions.get(transactionHash);
        return transaction;
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

    public synchronized List<SpendableTransactionOutput> getTransactionOutputsAndSpendableTokens(final SlpTokenId tokenId, final Boolean shouldIncludeNotYetValidatedTransactions) {
        final Collection<? extends SpendableTransactionOutput> spendableTransactionOutputs = _transactionOutputs.values();
        final ImmutableListBuilder<SpendableTransactionOutput> transactionOutputs = new ImmutableListBuilder<SpendableTransactionOutput>(spendableTransactionOutputs.size());
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            if ( (! _isSlpTokenOutput(transactionOutputIdentifier)) || (! _isSlpTransactionAndIsValid(transactionHash, shouldIncludeNotYetValidatedTransactions)) ) {
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
        return _getSlpTokens(null, true);
    }

    public synchronized List<SlpToken> getSlpTokens(final Boolean shouldIncludeNotYetValidatedTransactions) {
        return _getSlpTokens(null, shouldIncludeNotYetValidatedTransactions);
    }

    public synchronized List<SlpToken> getSlpTokens(final SlpTokenId slpTokenId) {
        return _getSlpTokens(slpTokenId, true);
    }

    public synchronized List<SlpToken> getSlpTokens(final SlpTokenId slpTokenId, final Boolean shouldIncludeNotYetValidatedTransactions) {
        return _getSlpTokens(slpTokenId, shouldIncludeNotYetValidatedTransactions);
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
        final Address address = addressInflater.fromPublicKey(publicKey, false);
        final Address compressedAddress = addressInflater.fromPublicKey(publicKey, true);

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

    public synchronized Long getBalance(final PublicKey publicKey, final SlpTokenId slpTokenId) {
        return _getBalance(publicKey, slpTokenId, true);
    }

    public synchronized Long getBalance(final PublicKey publicKey, final SlpTokenId slpTokenId, final Boolean shouldIncludeNotYetValidatedTransactions) {
        return _getBalance(publicKey, slpTokenId, shouldIncludeNotYetValidatedTransactions);
    }

    public synchronized Long getSlpTokenBalance(final SlpTokenId tokenId) {
        return _getSlpTokenBalance(tokenId, true);
    }

    public synchronized Long getSlpTokenBalance(final SlpTokenId tokenId, final Boolean shouldIncludeNotYetValidatedTransactions) {
        return _getSlpTokenBalance(tokenId, shouldIncludeNotYetValidatedTransactions);
    }

    public synchronized Long getInvalidSlpTokenBalance(final SlpTokenId tokenId) {
        long amount = 0L;
        for (final SpendableTransactionOutput spendableTransactionOutput : _transactionOutputs.values()) {
            if (! spendableTransactionOutput.isSpent()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

                // only include invalid transactions
                if (_invalidSlpTransactions.contains(transactionHash)) {
                    final Transaction transaction = _transactions.get(transactionHash);
                    if (transaction == null) { return null; }

                    final boolean outputContainsTokens = SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
                    if (! outputContainsTokens) { continue; }

                    final SlpTokenId transactionTokenId = SlpUtil.getTokenId(transaction);
                    if (! Util.areEqual(tokenId, transactionTokenId)) { continue; }

                    amount += SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
                }
            }
        }
        return amount;
    }

    public synchronized void markSlpTransactionAsValid(final Sha256Hash transactionHash) {
        if (_transactions.containsKey(transactionHash)) {
            _notYetValidatedSlpTransactions.remove(transactionHash);
            _validSlpTransactions.remove(transactionHash);
        }
        else {
            _validSlpTransactions.add(transactionHash);
        }
        // cannot be invalid now
        _invalidSlpTransactions.remove(transactionHash);

        Logger.debug(SlpValidity.VALID + " SLP transaction: " + transactionHash);
    }

    public synchronized void markSlpTransactionAsInvalid(final Sha256Hash transactionHash) {
        _notYetValidatedSlpTransactions.remove(transactionHash);
        _invalidSlpTransactions.add(transactionHash);
        // cannot be valid now
        _validSlpTransactions.remove(transactionHash);

        Logger.debug(SlpValidity.INVALID + " SLP transaction: " + transactionHash);
    }

    public synchronized void clearSlpValidity() {
        // mark explicitly tracked validity as unknown
        _notYetValidatedSlpTransactions.addAll(_validSlpTransactions);
        _notYetValidatedSlpTransactions.addAll(_invalidSlpTransactions);
        _validSlpTransactions.clear();
        _invalidSlpTransactions.clear();

        // mark implicitly tracked validity as unknown
        for (final Sha256Hash transactionHash : _transactions.keySet()) {
            final Transaction transaction = _transactions.get(transactionHash);
            if (Transaction.isSlpTransaction(transaction)) {
                _notYetValidatedSlpTransactions.add(transactionHash);
            }
        }
    }

    public synchronized Long getSlpTokenAmount(final SlpTokenId slpTokenId, final TransactionOutputIdentifier transactionOutputIdentifier) {
        final SlpTokenId outputSlpTokenId = _getSlpTokenId(transactionOutputIdentifier);
        if (! Util.areEqual(slpTokenId, outputSlpTokenId)) { return 0L; }

        return _getSlpTokenAmount(transactionOutputIdentifier);
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
            return addressInflater.fromPublicKey(publicKey, true);
        }

        return null;
    }

    public synchronized List<PublicKey> getPublicKeys() {
        return new ImmutableList<PublicKey>(_privateKeys.keySet());
    }
}
