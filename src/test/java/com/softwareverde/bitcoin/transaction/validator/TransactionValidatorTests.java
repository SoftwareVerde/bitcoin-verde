package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.TransactionTestUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.SignatureContextGenerator;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.network.time.ImmutableNetworkTime;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionValidatorTests extends IntegrationTest {
    static class StoredBlock {
        public final BlockId blockId;
        public final Block block;

        public StoredBlock(final BlockId blockId, final Block block) {
            this.blockId = blockId;
            this.block = block;
        }
    }

    protected StoredBlock _storeBlock(final String blockBytes) throws DatabaseException {
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockBytes));
        blockDatabaseManager.insertBlock(block);
        return new StoredBlock(blockHeaderDatabaseManager.getBlockHeaderId(block.getHash()), block);
    }

    public static MutableTransactionOutput _createTransactionOutput(final Address payToAddress, final Long amount) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setAmount(amount);
        transactionOutput.setIndex(0);
        transactionOutput.setLockingScript((ScriptBuilder.payToAddress(payToAddress)));
        return transactionOutput;
    }

    public static TransactionInput _createCoinbaseTransactionInput() {
        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
        mutableTransactionInput.setPreviousOutputTransactionHash(new MutableSha256Hash());
        mutableTransactionInput.setPreviousOutputIndex(-1);
        mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
        mutableTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").buildUnlockingScript());
        return mutableTransactionInput;
    }

    public static MutableTransactionInput _createTransactionInputThatSpendsTransaction(final Transaction transactionToSpend) {
        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
        mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
        mutableTransactionInput.setPreviousOutputIndex(0);
        mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
        mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
        return mutableTransactionInput;
    }

    public static MutableTransaction _createTransactionContaining(final TransactionInput transactionInput, final TransactionOutput transactionOutput) {
        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(1L);
        mutableTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));

        mutableTransaction.addTransactionInput(transactionInput);
        mutableTransaction.addTransactionOutput(transactionOutput);

        return mutableTransaction;
    }

    public static Long _calculateBlockHeight(final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        return databaseConnection.query(new Query("SELECT COUNT(*) AS block_height FROM blocks")).get(0).getLong("block_height");
    }

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_validate_valid_transaction() throws Exception {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

        final BlockChainSegmentId blockChainSegmentId;

        synchronized (BlockHeaderDatabaseManager.MUTEX) { // Store the transaction output being spent by the transaction...
            final StoredBlock storedBlock = _storeBlock(BlockData.MainChain.BLOCK_1);
            blockChainSegmentId = blockHeaderDatabaseManager.getBlockChainSegmentId(storedBlock.blockId);
            final Transaction previousTransaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001E7FCF39EE6B86F1595C55B16B60BF4F297988CB9519F5D42597E7FB721E591C6010000008B483045022100AC572B43E78089851202CFD9386750B08AFC175318C537F04EB364BF5A0070D402203F0E829D4BAEA982FEAF987CB9F14C85097D2FBE89FBA3F283F6925B3214A97E0141048922FA4DC891F9BB39F315635C03E60E019FF9EC1559C8B581324B4C3B7589A57550F9B0B80BC72D0F959FDDF6CA65F07223C37A8499076BD7027AE5C325FAC5FFFFFFFF0140420F00000000001976A914C4EB47ECFDCF609A1848EE79ACC2FA49D3CAAD7088AC00000000"));
            TransactionTestUtil.createRequiredTransactionInputs(blockChainSegmentId, previousTransaction, databaseConnection);
            final TransactionId transactionId = transactionDatabaseManager.insertTransaction(previousTransaction);
            transactionDatabaseManager.associateTransactionToBlock(transactionId, storedBlock.blockId);
        }

        final byte[] transactionBytes = HexUtil.hexStringToByteArray("01000000010B6072B386D4A773235237F64C1126AC3B240C84B917A3909BA1C43DED5F51F4000000008C493046022100BB1AD26DF930A51CCE110CF44F7A48C3C561FD977500B1AE5D6B6FD13D0B3F4A022100C5B42951ACEDFF14ABBA2736FD574BDB465F3E6F8DA12E2C5303954ACA7F78F3014104A7135BFE824C97ECC01EC7D7E336185C81E2AA2C41AB175407C09484CE9694B44953FCB751206564A9C24DD094D42FDBFDD5AAD3E063CE6AF4CFAAEA4EA14FBBFFFFFFFF0140420F00000000001976A91439AA3D569E06A1D7926DC4BE1193C99BF2EB9EE088AC00000000");
        final Transaction transaction = transactionInflater.fromBytes(transactionBytes);
        transactionDatabaseManager.insertTransaction(transaction);

        // Action
        final Boolean inputsAreUnlocked = transactionValidator.validateTransaction(blockChainSegmentId, _calculateBlockHeight(databaseConnection), transaction, true);

        // Assert
        Assert.assertTrue(inputsAreUnlocked);
    }

    @Test
    public void should_create_signed_transaction_and_unlock_it() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final TransactionSigner transactionSigner = new TransactionSigner();
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in our signed transaction.
        //  This transaction will create an output that can be spent by our private key.
        final Transaction transactionToSpend = _createTransactionContaining(
            _createCoinbaseTransactionInput(),
            _createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Store the transaction in the database so that our validator can access it.
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final StoredBlock storedBlock;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            storedBlock = _storeBlock(BlockData.MainChain.BLOCK_1);
        }
        final BlockChainSegmentId blockChainSegmentId = blockHeaderDatabaseManager.getBlockChainSegmentId(storedBlock.blockId);
        final TransactionId transactionId = transactionDatabaseManager.insertTransaction(transactionToSpend);
        transactionDatabaseManager.associateTransactionToBlock(transactionId, storedBlock.blockId);

        // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
        final Transaction unsignedTransaction = _createTransactionContaining(
            _createTransactionInputThatSpendsTransaction(transactionToSpend),
            _createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Sign the unsigned transaction.
        final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(databaseConnection, _databaseManagerCache);
        final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
        final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);
        transactionDatabaseManager.insertTransaction(signedTransaction);

        // Action
        final Boolean inputsAreUnlocked = transactionValidator.validateTransaction(blockChainSegmentId, _calculateBlockHeight(databaseConnection), signedTransaction, true);

        // Assert
        Assert.assertTrue(inputsAreUnlocked);
    }

    @Test
    public void should_detect_an_address_attempting_to_spend_an_output_it_cannot_unlock() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final TransactionSigner transactionSigner = new TransactionSigner();
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in our signed transaction.
        //  This transaction output is being sent to an address we don't have access to.
        final Transaction transactionToSpend = _createTransactionContaining(
            _createCoinbaseTransactionInput(),
            _createTransactionOutput(addressInflater.fromPrivateKey(PrivateKey.createNewKey()), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Store the transaction in the database so that our validator can access it.
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final StoredBlock storedBlock;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            storedBlock = _storeBlock(BlockData.MainChain.BLOCK_1);
        }
        final BlockChainSegmentId blockChainSegmentId = blockHeaderDatabaseManager.getBlockChainSegmentId(storedBlock.blockId);
        final TransactionId transactionId = transactionDatabaseManager.insertTransaction(transactionToSpend);
        transactionDatabaseManager.associateTransactionToBlock(transactionId, storedBlock.blockId);

        // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
        final Transaction unsignedTransaction = _createTransactionContaining(
            _createTransactionInputThatSpendsTransaction(transactionToSpend),
            _createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Sign the unsigned transaction with our key that does not match the address given to transactionToSpend.
        final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(databaseConnection, _databaseManagerCache);
        final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
        final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);

        // Action
        final Boolean inputsAreUnlocked = transactionValidator.validateTransaction(blockChainSegmentId, _calculateBlockHeight(databaseConnection), signedTransaction, true);

        // Assert
        Assert.assertFalse(inputsAreUnlocked);
    }

    @Test
    public void should_detect_an_address_attempting_to_spend_an_output_with_the_incorrect_signature() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final TransactionSigner transactionSigner = new TransactionSigner();
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in our signed transaction.
        //  This transaction output is being sent to an address we should have access to.
        final Transaction transactionToSpend = _createTransactionContaining(
            _createCoinbaseTransactionInput(),
            _createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Store the transaction in the database so that our validator can access it.
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final StoredBlock storedBlock;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            storedBlock = _storeBlock(BlockData.MainChain.BLOCK_1);
        }
        final BlockChainSegmentId blockChainSegmentId = blockHeaderDatabaseManager.getBlockChainSegmentId(storedBlock.blockId);
        final TransactionId transactionId = transactionDatabaseManager.insertTransaction(transactionToSpend);
        transactionDatabaseManager.associateTransactionToBlock(transactionId, storedBlock.blockId);

        // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
        final Transaction unsignedTransaction = _createTransactionContaining(
            _createTransactionInputThatSpendsTransaction(transactionToSpend),
            _createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Sign the unsigned transaction with our key that does not match the signature given to transactionToSpend.
        final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(databaseConnection, _databaseManagerCache);
        final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
        final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, PrivateKey.createNewKey());

        // Action
        final Boolean inputsAreUnlocked = transactionValidator.validateTransaction(blockChainSegmentId, _calculateBlockHeight(databaseConnection), signedTransaction, true);

        // Assert
        Assert.assertFalse(inputsAreUnlocked);
    }

    @Test
    public void should_not_validate_transaction_whose_inputs_spend_the_same_output() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final TransactionSigner transactionSigner = new TransactionSigner();
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
        final PrivateKey privateKey = PrivateKey.createNewKey();

        // Create a transaction that will be spent in our signed transaction.
        //  This transaction will create an output that can be spent by our private key.
        final Transaction transactionToSpend = _createTransactionContaining(
            _createCoinbaseTransactionInput(),
            _createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Store the transaction in the database so that our validator can access it.
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final StoredBlock storedBlock;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            storedBlock = _storeBlock(BlockData.MainChain.BLOCK_1);
        }
        final BlockChainSegmentId blockChainSegmentId = blockHeaderDatabaseManager.getBlockChainSegmentId(storedBlock.blockId);
        final TransactionId transactionId = transactionDatabaseManager.insertTransaction(transactionToSpend);
        transactionDatabaseManager.associateTransactionToBlock(transactionId, storedBlock.blockId);

        // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
        final MutableTransaction unsignedTransaction = _createTransactionContaining(
            _createTransactionInputThatSpendsTransaction(transactionToSpend),
            _createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
        );

        // Mutate the transaction so that it attempts to spend the same output twice...
        unsignedTransaction.addTransactionInput(unsignedTransaction.getTransactionInputs().get(0));

        // Sign the unsigned transaction.
        final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(databaseConnection, _databaseManagerCache);
        final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
        final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);

        // Action
        final Boolean transactionIsValid;
        {
            Boolean isValid;
            try {
                transactionDatabaseManager.insertTransaction(signedTransaction); // Should fail to insert transaction due to constraint transaction_inputs_tx_id_prev_tx_id_uq...
                isValid = transactionValidator.validateTransaction(blockChainSegmentId, _calculateBlockHeight(databaseConnection), signedTransaction, true);
            }
            catch (final DatabaseException exception) {
                isValid = false;
            }
            transactionIsValid = isValid;
        }

        // Assert
        Assert.assertFalse(transactionIsValid);
    }

    @Test
    public void should_not_validate_transaction_that_spends_the_same_input_twice() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockInflater blockInflater = new BlockInflater();
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionSigner transactionSigner = new TransactionSigner();
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

        Block lastBlock = null;
        BlockId lastBlockId = null;
        for (final String blockData : new String[] { BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                lastBlockId = blockDatabaseManager.storeBlock(block);
            }
            lastBlock = block;
        }
        Assert.assertNotNull(lastBlock);
        Assert.assertNotNull(lastBlockId);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Transaction transactionToSpend;
        final MutableBlock mutableBlock = new MutableBlock();
        {
            mutableBlock.setDifficulty(lastBlock.getDifficulty());
            mutableBlock.setNonce(lastBlock.getNonce());
            mutableBlock.setTimestamp(lastBlock.getTimestamp());
            mutableBlock.setPreviousBlockHash(lastBlock.getHash());
            mutableBlock.setVersion(lastBlock.getVersion());

            // Create a transaction that will be spent in our signed transaction.
            //  This transaction will create an output that can be spent by our private key.
            transactionToSpend = _createTransactionContaining(
                _createCoinbaseTransactionInput(),
                _createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 1L * Transaction.SATOSHIS_PER_BITCOIN)
            );

            mutableBlock.addTransaction(transactionToSpend);

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.storeBlock(mutableBlock);
            }
        }

        final Transaction signedTransaction;
        {
            // Create an unsigned transaction that spends our previous transaction, and send our payment to an irrelevant address.
            // The amount created by the input is greater than the input amount, and therefore, this Tx should not validate.
            final MutableTransaction unsignedTransaction = _createTransactionContaining(
                _createTransactionInputThatSpendsTransaction(transactionToSpend),
                _createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
            );

            // Sign the unsigned transaction.
            final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(databaseConnection, _databaseManagerCache);
            final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
            signedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);

            transactionDatabaseManager.insertTransaction(signedTransaction);
        }

        // Action
        final Boolean isValid = transactionValidator.validateTransaction(BlockChainSegmentId.wrap(1L), _calculateBlockHeight(databaseConnection), signedTransaction, true);

        // Assert
        Assert.assertFalse(isValid);
    }
}
