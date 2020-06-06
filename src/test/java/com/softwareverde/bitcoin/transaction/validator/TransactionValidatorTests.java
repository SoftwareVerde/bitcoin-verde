package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeUnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
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
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.constable.list.List;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionValidatorTests extends UnitTest {

    public static Transaction createTransactionSpendableByPrivateKey(final PrivateKey privateKey) {
        return TransactionValidatorTests.createTransactionSpendableByPrivateKey(privateKey, (50L * Transaction.SATOSHIS_PER_BITCOIN));
    }

    public static Transaction createTransactionSpendableByPrivateKey(final PrivateKey privateKey, final Long outputAmount) {
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

            final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.compressedFromPrivateKey(privateKey));
            mutableTransactionOutput.setLockingScript(lockingScript);
            transactionOutput = mutableTransactionOutput;
        }
        mutableTransaction.addTransactionOutput(transactionOutput);

        return mutableTransaction;
    }

    public static Transaction signTransaction(final Transaction transactionToSpend, final Transaction unsignedTransaction, final PrivateKey privateKey) {
        final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();
        int outputIndex = 0;
        for (final TransactionOutput transactionOutput : transactionToSpend.getTransactionOutputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), outputIndex);
            transactionOutputRepository.put(transactionOutputIdentifierToSpend, transactionOutput);
            outputIndex += 1;
        }

        Transaction partiallySignedTransaction = unsignedTransaction;
        final TransactionSigner transactionSigner = new TransactionSigner();

        int inputIndex = 0;
        final List<TransactionInput> transactionInputs = unsignedTransaction.getTransactionInputs();
        for (final TransactionInput transactionInput : transactionInputs) {
            final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutput transactionOutputBeingSpent = transactionOutputRepository.get(transactionOutputIdentifierBeingSpent);

            final SignatureContext signatureContext = new SignatureContext(partiallySignedTransaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false)); // BCH is not enabled at this block height...
            signatureContext.setInputIndexBeingSigned(inputIndex);
            signatureContext.setShouldSignInputScript(inputIndex, true, transactionOutputBeingSpent);
            partiallySignedTransaction = transactionSigner.signTransaction(signatureContext, privateKey, true);

            inputIndex += 1;
        }

        return partiallySignedTransaction;
    }

    @Before
    public void before() {
        super.before();
    }

    @After
    public void after() {
        super.after();
    }

    @Test
    public void should_validate_valid_transaction() throws Exception {
        // Setup
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final TransactionInflater transactionInflater = new TransactionInflater();

        final Transaction previousTransaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001E7FCF39EE6B86F1595C55B16B60BF4F297988CB9519F5D42597E7FB721E591C6010000008B483045022100AC572B43E78089851202CFD9386750B08AFC175318C537F04EB364BF5A0070D402203F0E829D4BAEA982FEAF987CB9F14C85097D2FBE89FBA3F283F6925B3214A97E0141048922FA4DC891F9BB39F315635C03E60E019FF9EC1559C8B581324B4C3B7589A57550F9B0B80BC72D0F959FDDF6CA65F07223C37A8499076BD7027AE5C325FAC5FFFFFFFF0140420F00000000001976A914C4EB47ECFDCF609A1848EE79ACC2FA49D3CAAD7088AC00000000"));
        unspentTransactionOutputContext.addTransaction(previousTransaction, Sha256Hash.fromHexString("0000000000004273D89D3F220A9D3DBD45A4FD1C028B51E170F478DA11352187"), 99811L, false);

        final byte[] transactionBytes = HexUtil.hexStringToByteArray("01000000010B6072B386D4A773235237F64C1126AC3B240C84B917A3909BA1C43DED5F51F4000000008C493046022100BB1AD26DF930A51CCE110CF44F7A48C3C561FD977500B1AE5D6B6FD13D0B3F4A022100C5B42951ACEDFF14ABBA2736FD574BDB465F3E6F8DA12E2C5303954ACA7F78F3014104A7135BFE824C97ECC01EC7D7E336185C81E2AA2C41AB175407C09484CE9694B44953FCB751206564A9C24DD094D42FDBFDD5AAD3E063CE6AF4CFAAEA4EA14FBBFFFFFFFF0140420F00000000001976A91439AA3D569E06A1D7926DC4BE1193C99BF2EB9EE088AC00000000");
        final Transaction transaction = transactionInflater.fromBytes(transactionBytes);
        final Long blockHeight = 100000L;

        // Action
        final Boolean outputsAreUnlocked = transactionValidator.validateTransaction(blockHeight, transaction);

        // Assert
        Assert.assertTrue(outputsAreUnlocked);
    }

    @Test
    public void should_create_signed_transaction_and_unlock_it() throws Exception {
        // Setup
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final AddressInflater addressInflater = new AddressInflater();
        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in our signed transaction.
        //  This transaction creates an output that can be spent by our private key.
        final Transaction transactionToSpend = TransactionValidatorTests.createTransactionSpendableByPrivateKey(privateKey);
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = new MutableTransaction();
            mutableTransaction.setVersion(Transaction.VERSION);
            mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

            final TransactionInput transactionInput;
            {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                mutableTransactionInput.setPreviousOutputIndex(0);
                mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                transactionInput = mutableTransactionInput;
            }
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput;
            {
                final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                mutableTransactionOutput.setIndex(0);
                mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);

                final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.compressedFromPrivateKey(privateKey));
                mutableTransactionOutput.setLockingScript(lockingScript);
                transactionOutput = mutableTransactionOutput;
            }
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        final Transaction signedTransaction = TransactionValidatorTests.signTransaction(transactionToSpend, unsignedTransaction, privateKey);

        // Action
        final Boolean outputsAreUnlocked = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertTrue(outputsAreUnlocked);
    }

    @Test
    public void should_not_validate_a_transaction_attempting_to_spend_an_output_with_the_wrong_key() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in our signed transaction.
        //  This transaction output is being sent to an address we don't have access to.
        final Transaction transactionToSpend = TransactionValidatorTests.createTransactionSpendableByPrivateKey(PrivateKey.createNewKey());
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = new MutableTransaction();
            mutableTransaction.setVersion(Transaction.VERSION);
            mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

            final TransactionInput transactionInput;
            {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                mutableTransactionInput.setPreviousOutputIndex(0);
                mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                transactionInput = mutableTransactionInput;
            }
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput;
            {
                final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                mutableTransactionOutput.setIndex(0);
                mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);

                final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.compressedFromPrivateKey(privateKey));
                mutableTransactionOutput.setLockingScript(lockingScript);
                transactionOutput = mutableTransactionOutput;
            }
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        // Sign the unsigned transaction with our key that does not match the address given to transactionToSpend.
        final Transaction signedTransaction = TransactionValidatorTests.signTransaction(transactionToSpend, unsignedTransaction, privateKey);

        // Action
        final Boolean outputsAreUnlocked = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertFalse(outputsAreUnlocked);
    }

    @Test
    public void should_not_validate_transaction_that_spends_the_same_input_twice() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in our signed transaction.
        //  This transaction will create an output that can be spent by our private key.
        final Transaction transactionToSpend = TransactionValidatorTests.createTransactionSpendableByPrivateKey(privateKey);
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = new MutableTransaction();
            mutableTransaction.setVersion(Transaction.VERSION);
            mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

            // Add two inputs that attempt to spend the same output...
            for (int i = 0; i < 2; ++i) {
                final TransactionInput transactionInput;
                {
                    final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                    mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                    mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                    mutableTransactionInput.setPreviousOutputIndex(0);
                    mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                    transactionInput = mutableTransactionInput;
                }
                mutableTransaction.addTransactionInput(transactionInput);
            }

            final TransactionOutput transactionOutput;
            {
                final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                mutableTransactionOutput.setIndex(0);
                mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);

                final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.compressedFromPrivateKey(privateKey));
                mutableTransactionOutput.setLockingScript(lockingScript);
                transactionOutput = mutableTransactionOutput;
            }
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        // Sign the unsigned transaction.
        final Transaction signedTransaction = TransactionValidatorTests.signTransaction(transactionToSpend, unsignedTransaction, privateKey);

        // Action
        final Boolean outputsAreUnlocked = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertFalse(outputsAreUnlocked);
    }

    @Test
    public void should_not_validate_transaction_that_spends_more_than_the_input_amount() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Transaction transactionToSpend = TransactionValidatorTests.createTransactionSpendableByPrivateKey(privateKey, (10L * Transaction.SATOSHIS_PER_BITCOIN));
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends more than our previous transaction provides...
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = new MutableTransaction();
            mutableTransaction.setVersion(Transaction.VERSION);
            mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

            final TransactionInput transactionInput;
            {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                mutableTransactionInput.setPreviousOutputIndex(0);
                mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                transactionInput = mutableTransactionInput;
            }
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput;
            {
                final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                mutableTransactionOutput.setIndex(0);
                mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);

                final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.compressedFromPrivateKey(privateKey));
                mutableTransactionOutput.setLockingScript(lockingScript);
                transactionOutput = mutableTransactionOutput;
            }
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        final Transaction signedTransaction = TransactionValidatorTests.signTransaction(transactionToSpend, unsignedTransaction, privateKey);

        // Action
        final Boolean isValid = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertFalse(isValid);
    }

    @Test
    public void should_not_accept_transaction_with_previous_output_that_does_not_exist() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(new MutableNetworkTime(), MedianBlockTime.MAX_VALUE, unspentTransactionOutputContext);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Transaction transactionToSpend = TransactionValidatorTests.createTransactionSpendableByPrivateKey(privateKey);
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        final int outputIndexToSpend = 1; // Is greater than the number of outputs within transactionToSpend...

        // Create an unsigned transaction that spends an unknown/unavailable output (previous output index does not exist)...
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = new MutableTransaction();
            mutableTransaction.setVersion(Transaction.VERSION);
            mutableTransaction.setLockTime(LockTime.MAX_TIMESTAMP);

            final TransactionInput transactionInput;
            {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                mutableTransactionInput.setPreviousOutputIndex(outputIndexToSpend); // NOTE: Output index does not exist.
                mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                transactionInput = mutableTransactionInput;
            }
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput;
            {
                final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                mutableTransactionOutput.setIndex(0);
                mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);

                final LockingScript lockingScript = ScriptBuilder.payToAddress(addressInflater.uncompressedFromBase58Check("149uLAy8vkn1Gm68t5NoLQtUqBtngjySLF"));
                mutableTransactionOutput.setLockingScript(lockingScript);
                transactionOutput = mutableTransactionOutput;
            }
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        final Transaction signedTransaction;
        { // Sign the transaction as if it was spending the output that does actually exist to ensure the Transaction is invalid only because of the non-existing output, not a bad signature...
            final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();
            final List<TransactionOutput> transactionOutputsToSpend = transactionToSpend.getTransactionOutputs();
            transactionOutputRepository.put(new TransactionOutputIdentifier(transactionToSpend.getHash(), outputIndexToSpend), transactionOutputsToSpend.get(0));

            Transaction partiallySignedTransaction = unsignedTransaction;
            final TransactionSigner transactionSigner = new TransactionSigner();

            int inputIndex = 0;
            final List<TransactionInput> transactionInputs = unsignedTransaction.getTransactionInputs();
            for (final TransactionInput transactionInput : transactionInputs) {
                final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final TransactionOutput transactionOutputBeingSpent = transactionOutputRepository.get(transactionOutputIdentifierBeingSpent);

                final SignatureContext signatureContext = new SignatureContext(partiallySignedTransaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false)); // BCH is not enabled at this block height...
                signatureContext.setInputIndexBeingSigned(inputIndex);
                signatureContext.setShouldSignInputScript(inputIndex, true, transactionOutputBeingSpent);
                partiallySignedTransaction = transactionSigner.signTransaction(signatureContext, privateKey, true);

                inputIndex += 1;
            }

            signedTransaction = partiallySignedTransaction;
        }

        // Action
        final Boolean doubleSpendIsValid = transactionValidator.validateTransaction(2L, signedTransaction);

        // Assert
        Assert.assertFalse(doubleSpendIsValid);
    }
}
