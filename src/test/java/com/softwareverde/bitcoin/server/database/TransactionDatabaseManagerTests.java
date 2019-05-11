package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidatorTests;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.server.module.node.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.signer.*;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorTests;
import com.softwareverde.network.time.ImmutableNetworkTime;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionDatabaseManagerTests extends IntegrationTest {

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void duplicate_transaction_should_be_rejected() throws Exception {

    }

    @Test
    public void transaction_spending_output_spent_by_other_mempool_tx_should_be_invalid() throws Exception {
        final DatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockInflater blockInflater = new BlockInflater();
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionSigner transactionSigner = new TransactionSigner();
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new ImmutableMedianBlockTime(Long.MAX_VALUE));
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockValidator blockValidator = new BlockValidator(_database.getDatabaseConnectionFactory(), _databaseManagerCache, new ImmutableNetworkTime(Long.MAX_VALUE), new BlockValidatorTests.FakeMedianBlockTime());

        final TransactionOutputRepository transactionOutputRepository = new DatabaseTransactionOutputRepository(databaseConnection, _databaseManagerCache);

        Sha256Hash lastBlockHash = null;
        Block lastBlock = null;
        BlockId lastBlockId = null;
        for (final String blockData : new String[] { BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2 }) {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                lastBlockId = blockDatabaseManager.storeBlock(block);
            }
            lastBlock = block;
            lastBlockHash = block.getHash();
        }
        Assert.assertNotNull(lastBlock);
        Assert.assertNotNull(lastBlockId);
        Assert.assertNotNull(lastBlockHash);

        final PrivateKey privateKey = PrivateKey.createNewKey();

        final Transaction spendableCoinbase;
        final MutableBlock blockWithSpendableCoinbase = new MutableBlock() {
            @Override
            public Boolean isValid() { return true; } // Disables basic header validation...
        };

        {
            blockWithSpendableCoinbase.setDifficulty(lastBlock.getDifficulty());
            blockWithSpendableCoinbase.setNonce(lastBlock.getNonce());
            blockWithSpendableCoinbase.setTimestamp(lastBlock.getTimestamp());
            blockWithSpendableCoinbase.setVersion(lastBlock.getVersion());

            // Create a transaction that will be spent in our signed transaction.
            //  This transaction will create an output that can be spent by our private key.
            spendableCoinbase = TransactionValidatorTests._createTransactionContaining(
                TransactionValidatorTests._createCoinbaseTransactionInput(),
                TransactionValidatorTests._createTransactionOutput(addressInflater.fromPrivateKey(privateKey), 50L * Transaction.SATOSHIS_PER_BITCOIN)
            );

            blockWithSpendableCoinbase.addTransaction(spendableCoinbase);

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockWithSpendableCoinbase.setPreviousBlockHash(lastBlockHash);
                final BlockId blockId = blockDatabaseManager.storeBlock(blockWithSpendableCoinbase); // Block3
                lastBlockHash = blockWithSpendableCoinbase.getHash();

                final Boolean blockIsValid = blockValidator.validateBlock(blockId, blockWithSpendableCoinbase).isValid;
                Assert.assertTrue(blockIsValid);
            }
        }

        final TransactionId transactionId0;
        final Transaction transaction0;
        {
            final MutableTransaction unsignedTransaction = TransactionValidatorTests._createTransactionContaining(
                TransactionValidatorTests._createTransactionInputThatSpendsTransaction(spendableCoinbase),
                TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
            );

            // Sign the transaction..
            final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(transactionOutputRepository);
            final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
            transaction0 = transactionSigner.signTransaction(signatureContext, privateKey);

            transactionId0 = transactionDatabaseManager.storeTransaction(transaction0);
        }

        final TransactionId transactionId1;
        final Transaction transaction1;
        {
            final MutableTransaction unsignedTransaction = TransactionValidatorTests._createTransactionContaining(
                TransactionValidatorTests._createTransactionInputThatSpendsTransaction(spendableCoinbase),
                TransactionValidatorTests._createTransactionOutput(addressInflater.fromBase58Check("13usM2ns3f466LP65EY1h8hnTBLFiJV6rD"), 50L * Transaction.SATOSHIS_PER_BITCOIN)
            );

            // Sign the transaction..
            final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(transactionOutputRepository);
            final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(unsignedTransaction, false);
            transaction1 = transactionSigner.signTransaction(signatureContext, privateKey);

            transactionId1 = transactionDatabaseManager.storeTransaction(transaction1);
        }

        { // Assert transaction0 is valid before adding to mempool...
            final Boolean isValid = transactionValidator.validateTransaction(BlockchainSegmentId.wrap(1L), TransactionValidatorTests._calculateBlockHeight(databaseConnection), transaction0, true);
            Assert.assertTrue(isValid);
        }

        { // Assert transaction1 is valid as well...
            final Boolean isValid = transactionValidator.validateTransaction(BlockchainSegmentId.wrap(1L), TransactionValidatorTests._calculateBlockHeight(databaseConnection), transaction1, true);
            Assert.assertTrue(isValid);
        }

        // Action
        transactionDatabaseManager.addToUnconfirmedTransactions(transactionId0);

        // Assert
        final Boolean isValid = transactionValidator.validateTransaction(BlockchainSegmentId.wrap(1L), TransactionValidatorTests._calculateBlockHeight(databaseConnection), transaction1, true);
        Assert.assertFalse(isValid); // Should no longer be valid since transaction0 was added to the mempool...
    }
}
