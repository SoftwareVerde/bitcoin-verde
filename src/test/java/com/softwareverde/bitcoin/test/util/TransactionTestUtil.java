package com.softwareverde.bitcoin.test.util;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.HashMapTransactionOutputRepository;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionOutputRepository;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.util.Util;


public class TransactionTestUtil {
    protected TransactionTestUtil() { }

    public static MutableTransaction createTransaction() {
        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(Transaction.VERSION);
        mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);
        return mutableTransaction;
    }

    public static HashMapTransactionOutputRepository createTransactionOutputRepository(final Transaction... transactionsToSpend) {
        final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();
        for (final Transaction transactionToSpend : transactionsToSpend) {
            int outputIndex = 0;
            for (final TransactionOutput transactionOutput : transactionToSpend.getTransactionOutputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), outputIndex);
                transactionOutputRepository.put(transactionOutputIdentifierToSpend, transactionOutput);
                outputIndex += 1;
            }
        }
        return transactionOutputRepository;
    }

    /**
     * Creates a signed transaction using the PrivateKey(s) provided.
     *  One or more PrivateKeys may be provided.
     *  If multiple PrivateKeys are provided then the order used corresponds to the Transaction's TransactionInputs.
     *  The number of PrivateKeys may be less than the number of TransactionInputs, in which case the last PrivateKey will be used to sign the remaining TransactionInputs.
     */
    public static Transaction signTransaction(final TransactionOutputRepository transactionOutputRepository, final Transaction unsignedTransaction, final PrivateKey... privateKeys) {
        Transaction partiallySignedTransaction = unsignedTransaction;
        final List<TransactionInput> transactionInputs = unsignedTransaction.getTransactionInputs();
        final TransactionSigner transactionSigner = new TransactionSigner();

        int privateKeyIndex = 0;

        final MutableList<TransactionOutput> transactionOutputsToSpend = new MutableArrayList<>();
        for (final TransactionInput transactionInput : transactionInputs) {
            final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutput previousTransactionOutput = transactionOutputRepository.get(transactionOutputIdentifierBeingSpent);
            transactionOutputsToSpend.add(previousTransactionOutput);
        }

        for (int inputIndex = 0; inputIndex < transactionInputs.getCount(); ++inputIndex) {
            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
            final SignatureContext signatureContext = new SignatureContext(
                partiallySignedTransaction,
                new HashType(Mode.SIGNATURE_HASH_ALL, true, false, false), // BCH is not enabled at this block height...
                transactionOutputsToSpend,
                upgradeSchedule
            );
            signatureContext.setInputIndexBeingSigned(inputIndex);
            signatureContext.setShouldSignInputScript(inputIndex, true);

            final PrivateKey privateKey = privateKeys[privateKeyIndex];
            if ((privateKeyIndex + 1) < privateKeys.length) {
                privateKeyIndex += 1;
            }

            partiallySignedTransaction = transactionSigner.signTransaction(signatureContext, privateKey, true);
        }

        return partiallySignedTransaction;
    }

    public static MutableTransactionInput createTransactionInput(final TransactionOutputIdentifier transactionOutputIdentifierToSpend) {
        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
        mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
        mutableTransactionInput.setPreviousOutputTransactionHash(transactionOutputIdentifierToSpend.getTransactionHash());
        mutableTransactionInput.setPreviousOutputIndex(transactionOutputIdentifierToSpend.getOutputIndex());
        mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
        return mutableTransactionInput;
    }

    public static MutableTransactionOutput createTransactionOutput(final Address address) {
        return TransactionTestUtil.createTransactionOutput((50L * Transaction.SATOSHIS_PER_BITCOIN), address);
    }

    public static MutableTransactionOutput createTransactionOutput(final Long amount, final Address address) {
        final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
        mutableTransactionOutput.setAmount(amount);

        final LockingScript lockingScript = ScriptBuilder.payToAddress(address);
        mutableTransactionOutput.setLockingScript(lockingScript);

        return mutableTransactionOutput;
    }

    public static Transaction createCoinbaseTransactionSpendableByPrivateKey(final PrivateKey privateKey) {
        return TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey, (50L * Transaction.SATOSHIS_PER_BITCOIN));
    }

    public static Transaction createCoinbaseTransactionSpendableByPrivateKey(final PrivateKey privateKey, final Long outputAmount) {
        final AddressInflater addressInflater = new AddressInflater();

        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(Transaction.VERSION);
        mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

        final TransactionInput transactionInput;
        {
            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
            mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
            mutableTransactionInput.setPreviousOutputTransactionHash(Sha256Hash.EMPTY_HASH);
            mutableTransactionInput.setPreviousOutputIndex(-1);
            mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
            transactionInput = mutableTransactionInput;
        }
        mutableTransaction.addTransactionInput(transactionInput);

        final TransactionOutput transactionOutput;
        {
            final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
            mutableTransactionOutput.setIndex(0);
            mutableTransactionOutput.setAmount(outputAmount);

            final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.fromPrivateKey(privateKey, true));
            mutableTransactionOutput.setLockingScript(lockingScript);
            transactionOutput = mutableTransactionOutput;
        }
        mutableTransaction.addTransactionOutput(transactionOutput);

        return mutableTransaction;
    }

    public static Map<TransactionOutputIdentifier, TransactionOutput> createPreviousTransactionOutputsMap(final List<TransactionInput> transactionInputs, final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput) {
        final MutableMap<TransactionOutputIdentifier, TransactionOutput> outputMap = new MutableHashMap<>();
        for (final TransactionInput transactionInput : transactionInputs) {
            final TransactionOutputIdentifier previousOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            if (Util.areEqual(transactionOutputIdentifier, previousOutputIdentifier)) {
                outputMap.put(transactionOutputIdentifier, transactionOutput);
            }
            else {
                outputMap.put(transactionOutputIdentifier, null);
            }
        }
        return outputMap;
    }

    public static MutableList<TransactionOutput> createPreviousTransactionOutputsList(final Integer transactionInputCount, final Integer outputIndex, final TransactionOutput transactionOutput) {
        final MutableList<TransactionOutput> previousTransactionOutputsList = new MutableArrayList<>(transactionInputCount);
        for (int i = 0; i < transactionInputCount; ++i) {
            previousTransactionOutputsList.add(i == outputIndex ? transactionOutput : null);
        }
        return previousTransactionOutputsList;
    }

    public static MutableList<TransactionOutput> createPreviousTransactionOutputsList(final List<TransactionInput> transactionInputs, final Map<Sha256Hash, Transaction> transactionsToSpend) {
        final int transactionInputCount = transactionInputs.getCount();
        final MutableList<TransactionOutput> previousTransactionOutputsList = new MutableArrayList<>(transactionInputCount);
        for (int i = 0; i < transactionInputCount; ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer transactionOutputIndex = transactionInput.getPreviousOutputIndex();

            final Transaction transactionToSpend = transactionsToSpend.get(previousTransactionHash);
            final List<TransactionOutput> transactionOutputs = (transactionToSpend != null ? transactionToSpend.getTransactionOutputs() : null);
            final TransactionOutput transactionOutput = (transactionOutputs != null ? transactionOutputs.get(transactionOutputIndex) : null);

            if (transactionOutput == null) {
                throw new RuntimeException("Missing Previous Transaction Output: " + previousTransactionHash + ":" + transactionOutputIndex);
            }

            previousTransactionOutputsList.add(transactionOutput);
        }
        return previousTransactionOutputsList;
    }
}
