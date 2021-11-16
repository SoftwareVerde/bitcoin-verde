package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeStaticMedianBlockTimeContext;
import com.softwareverde.bitcoin.test.fake.FakeUnspentTransactionOutputContext;
import com.softwareverde.bitcoin.test.util.TransactionTestUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.signer.HashMapTransactionOutputRepository;
import com.softwareverde.bitcoin.transaction.signer.TransactionOutputRepository;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionValidatorTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_validate_valid_transaction() throws Exception {
        // Setup
        final MasterInflater masterInflater = new CoreInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(masterInflater, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final TransactionInflater transactionInflater = new TransactionInflater();

        final Transaction previousTransaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001E7FCF39EE6B86F1595C55B16B60BF4F297988CB9519F5D42597E7FB721E591C6010000008B483045022100AC572B43E78089851202CFD9386750B08AFC175318C537F04EB364BF5A0070D402203F0E829D4BAEA982FEAF987CB9F14C85097D2FBE89FBA3F283F6925B3214A97E0141048922FA4DC891F9BB39F315635C03E60E019FF9EC1559C8B581324B4C3B7589A57550F9B0B80BC72D0F959FDDF6CA65F07223C37A8499076BD7027AE5C325FAC5FFFFFFFF0140420F00000000001976A914C4EB47ECFDCF609A1848EE79ACC2FA49D3CAAD7088AC00000000"));
        unspentTransactionOutputContext.addTransaction(previousTransaction, Sha256Hash.fromHexString("0000000000004273D89D3F220A9D3DBD45A4FD1C028B51E170F478DA11352187"), 99811L, false);

        final byte[] transactionBytes = HexUtil.hexStringToByteArray("01000000010B6072B386D4A773235237F64C1126AC3B240C84B917A3909BA1C43DED5F51F4000000008C493046022100BB1AD26DF930A51CCE110CF44F7A48C3C561FD977500B1AE5D6B6FD13D0B3F4A022100C5B42951ACEDFF14ABBA2736FD574BDB465F3E6F8DA12E2C5303954ACA7F78F3014104A7135BFE824C97ECC01EC7D7E336185C81E2AA2C41AB175407C09484CE9694B44953FCB751206564A9C24DD094D42FDBFDD5AAD3E063CE6AF4CFAAEA4EA14FBBFFFFFFFF0140420F00000000001976A91439AA3D569E06A1D7926DC4BE1193C99BF2EB9EE088AC00000000");
        final Transaction transaction = transactionInflater.fromBytes(transactionBytes);
        final Long blockHeight = 100000L;

        // Action
        final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(blockHeight, transaction);

        // Assert
        Assert.assertTrue(transactionValidationResult.isValid);
    }

    @Test
    public void should_create_signed_transaction_and_unlock_it() throws Exception {
        // Setup
        final MasterInflater masterInflater = new CoreInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(masterInflater, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final AddressInflater addressInflater = new AddressInflater();
        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in the test's signed transaction.
        //  This transaction creates an output that can be spent by the test's private key.
        final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey);
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends the test's previous transaction, and send the test's payment to an irrelevant address.
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

            final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
            final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(addressInflater.fromPrivateKey(privateKey, true));
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
        final Transaction signedTransaction = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);

        // Action
        final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertTrue(transactionValidationResult.isValid);
    }

    @Test
    public void should_not_validate_a_transaction_attempting_to_spend_an_output_with_the_wrong_key() throws Exception {
        // Setup
        final MasterInflater masterInflater = new CoreInflater();
        final AddressInflater addressInflater = masterInflater.getAddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(masterInflater, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in the test's signed transaction.
        //  This transaction output is being sent to an address we don't have access to.
        final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(PrivateKey.createNewKey());
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends the test's previous transaction, and send the test's payment to an irrelevant address.
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
            final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifier);
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(addressInflater.fromPrivateKey(privateKey, true));
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        // Sign the unsigned transaction with the test's key that does not match the address given to transactionToSpend.
        final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
        final Transaction signedTransaction = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);

        // Action
        final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertFalse(transactionValidationResult.isValid);
    }

    @Test
    public void should_not_validate_transaction_that_spends_the_same_input_twice() throws Exception {
        // Setup
        final MasterInflater masterInflater = new CoreInflater();
        final AddressInflater addressInflater = masterInflater.getAddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(masterInflater, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in the test's signed transaction.
        //  This transaction will create an output that can be spent by the test's private key.
        final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey);
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends the test's previous transaction, and send the test's payment to an irrelevant address.
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

            // Add two inputs that attempt to spend the same output...
            final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
            final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
            mutableTransaction.addTransactionInput(transactionInput);
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(addressInflater.fromPrivateKey(privateKey, true));
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        // Sign the unsigned transaction.
        final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
        final Transaction signedTransaction = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);

        // Action
        final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertFalse(transactionValidationResult.isValid);
    }

    @Test
    public void should_not_validate_transaction_that_spends_more_than_the_input_amount() throws Exception {
        // Setup
        final MasterInflater masterInflater = new CoreInflater();
        final AddressInflater addressInflater = masterInflater.getAddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(masterInflater, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey, (10L * Transaction.SATOSHIS_PER_BITCOIN));
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        // Create an unsigned transaction that spends more than the test's previous transaction provides...
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

            final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
            final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
            mutableTransaction.addTransactionInput(transactionInput);

            // NOTE: The output amount is greater than the coinbase amount.
            final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput((50L * Transaction.SATOSHIS_PER_BITCOIN), addressInflater.fromPrivateKey(privateKey, true));
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        final TransactionOutputRepository transactionOutputRepository = TransactionTestUtil.createTransactionOutputRepository(transactionToSpend);
        final Transaction signedTransaction = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);

        // Action
        final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(1L, signedTransaction);

        // Assert
        Assert.assertFalse(transactionValidationResult.isValid);
    }

    @Test
    public void should_not_accept_transaction_with_previous_output_that_does_not_exist() throws Exception {
        // Setup
        final MasterInflater masterInflater = new CoreInflater();
        final AddressInflater addressInflater = masterInflater.getAddressInflater();
        final FakeUnspentTransactionOutputContext unspentTransactionOutputContext = new FakeUnspentTransactionOutputContext();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(masterInflater, new MutableNetworkTime(), FakeStaticMedianBlockTimeContext.MAX_MEDIAN_BLOCK_TIME, unspentTransactionOutputContext, upgradeSchedule);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Transaction transactionToSpend = TransactionTestUtil.createCoinbaseTransactionSpendableByPrivateKey(privateKey);
        unspentTransactionOutputContext.addTransaction(transactionToSpend, null, 1L, false);

        final int outputIndexToSpend = 1; // Is greater than the number of outputs within transactionToSpend...

        // Create an unsigned transaction that spends an unknown/unavailable output (previous output index does not exist)...
        final Transaction unsignedTransaction;
        {
            final MutableTransaction mutableTransaction = TransactionTestUtil.createTransaction();

            final TransactionOutputIdentifier transactionOutputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), outputIndexToSpend);  // NOTE: Output index does not exist.
            final TransactionInput transactionInput = TransactionTestUtil.createTransactionInput(transactionOutputIdentifierToSpend);
            mutableTransaction.addTransactionInput(transactionInput);

            final TransactionOutput transactionOutput = TransactionTestUtil.createTransactionOutput(addressInflater.fromBase58Check("149uLAy8vkn1Gm68t5NoLQtUqBtngjySLF", false));
            mutableTransaction.addTransactionOutput(transactionOutput);

            unsignedTransaction = mutableTransaction;
        }

        final Transaction signedTransaction;
        { // Sign the transaction as if it was spending the output that does actually exist to ensure the Transaction is invalid only because of the non-existing output, not a bad signature...
            final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();
            final List<TransactionOutput> transactionOutputsToSpend = transactionToSpend.getTransactionOutputs();
            transactionOutputRepository.put(new TransactionOutputIdentifier(transactionToSpend.getHash(), outputIndexToSpend), transactionOutputsToSpend.get(0));

            signedTransaction = TransactionTestUtil.signTransaction(transactionOutputRepository, unsignedTransaction, privateKey);
        }

        // Action
        final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(2L, signedTransaction);

        // Assert
        Assert.assertFalse(transactionValidationResult.isValid);
    }
}
