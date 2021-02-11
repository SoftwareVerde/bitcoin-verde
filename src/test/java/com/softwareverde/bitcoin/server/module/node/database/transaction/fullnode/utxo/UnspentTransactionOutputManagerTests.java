package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UnspentTransactionOutputManagerTests extends IntegrationTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_remove_created_utxos_and_restore_spent_outputs_when_removing_block_from_utxo_set() throws Exception {
        // Setup
        final AddressInflater addressInflater = _masterInflater.getAddressInflater();
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, 2016L);

            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final Block block0 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK));

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.storeBlock(block0);
            }

            final Address address0 = addressInflater.fromPrivateKey(PrivateKey.createNewKey());
            final Address address1 = addressInflater.fromPrivateKey(PrivateKey.createNewKey());
            final Address address2 = addressInflater.fromPrivateKey(PrivateKey.createNewKey());

            final Block block1;
            final Block block2;
            final CoinbaseTransaction block1CoinbaseTransaction;
            final Transaction block1Transaction1;
            final Transaction block2CoinbaseTransaction;
            final Transaction block2Transaction1;
            final Transaction block2Transaction2;
            {
                final MutableBlock mutableBlock = new MutableBlock();
                { // Init Block Template...
                    mutableBlock.setVersion(Block.VERSION);
                    mutableBlock.setPreviousBlockHash(BlockHeader.GENESIS_BLOCK_HASH);
                    mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
                    mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
                    mutableBlock.setNonce(0L);
                }

                {
                    final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                    { // Init Transaction Input Template...
                        mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                        mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                    }

                    final MutableTransaction mutableTransaction = new MutableTransaction();
                    { // Init Transaction Template...
                        mutableTransaction.setVersion(Transaction.VERSION);
                        mutableTransaction.setLockTime(LockTime.MIN_TIMESTAMP);
                    }

                    { // Create Block1's CoinbaseTransaction; generates 50 BCH.
                        mutableTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInput(1L, ""));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address0, 50L * Transaction.SATOSHIS_PER_BITCOIN));
                        block1CoinbaseTransaction = mutableTransaction.asCoinbase();
                    }

                    { // Create Block1's second Transaction that spends Block1's Coinbase and creates two 25BCH outputs.
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        { // Configure TransactionInput to spent Block1's Coinbase Transaction.
                            mutableTransactionInput.setPreviousOutputTransactionHash(block1CoinbaseTransaction.getHash());
                            mutableTransactionInput.setPreviousOutputIndex(0);
                        }
                        mutableTransaction.addTransactionInput(mutableTransactionInput.asConst());
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 25L * Transaction.SATOSHIS_PER_BITCOIN));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address2, 25L * Transaction.SATOSHIS_PER_BITCOIN));
                        block1Transaction1 = mutableTransaction.asConst();
                    }

                    { // Create Block2's CoinbaseTransaction; generates 50 BCH.
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        mutableTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInput(2L, ""));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 50L * Transaction.SATOSHIS_PER_BITCOIN));
                        block2CoinbaseTransaction = mutableTransaction.asCoinbase();
                    }

                    { // Create Block2's second Transaction that spends Block1's second Transaction first TransactionOutput...
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        { // Configure TransactionInput to spent Block1's Coinbase Transaction.
                            mutableTransactionInput.setPreviousOutputTransactionHash(block1Transaction1.getHash());
                            mutableTransactionInput.setPreviousOutputIndex(0);
                        }
                        mutableTransaction.addTransactionInput(mutableTransactionInput.asConst());
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 12L * Transaction.SATOSHIS_PER_BITCOIN));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address2, 13L * Transaction.SATOSHIS_PER_BITCOIN));
                        block2Transaction1 = mutableTransaction.asConst();
                    }

                    { // Create Block2's third Transaction that spends Block2's second Transaction second TransactionOutput...
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        { // Configure TransactionInput to spent Block1's Coinbase Transaction.
                            mutableTransactionInput.setPreviousOutputTransactionHash(block2Transaction1.getHash());
                            mutableTransactionInput.setPreviousOutputIndex(1);
                        }
                        mutableTransaction.addTransactionInput(mutableTransactionInput.asConst());
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 10L * Transaction.SATOSHIS_PER_BITCOIN));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address2, 3L * Transaction.SATOSHIS_PER_BITCOIN));
                        block2Transaction2 = mutableTransaction.asConst();
                    }
                }

                { // Assemble Block 1...
                    mutableBlock.addTransaction(block1CoinbaseTransaction);
                    mutableBlock.addTransaction(block1Transaction1);
                    block1 = mutableBlock.asConst();
                }

                { // Assemble Block 2...
                    mutableBlock.clearTransactions();
                    mutableBlock.addTransaction(block2CoinbaseTransaction);
                    mutableBlock.addTransaction(block2Transaction1);
                    mutableBlock.addTransaction(block2Transaction2);
                    block2 = mutableBlock.asConst();
                }
            }

            { // Store and sanity-check Block1 state...
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.storeBlock(block1);
                }
                unspentTransactionOutputManager.applyBlockToUtxoSet(block1, 1L, _fullNodeDatabaseManagerFactory);

                final TransactionOutput coinbaseTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1CoinbaseTransaction.getHash(), 0));
                Assert.assertNull(coinbaseTransactionOutput); // Spent by Block1 Transaction1...

                final TransactionOutput transactionOutput0 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 0));
                Assert.assertNotNull(transactionOutput0);

                final TransactionOutput transactionOutput1 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 1));
                Assert.assertNotNull(transactionOutput1);
            }


            { // Store and sanity-check Block2 state...
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.storeBlock(block2);
                }
                unspentTransactionOutputManager.applyBlockToUtxoSet(block2, 2L, _fullNodeDatabaseManagerFactory);

                final TransactionOutput coinbaseTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1CoinbaseTransaction.getHash(), 0));
                Assert.assertNull(coinbaseTransactionOutput); // Spent by Block1's Transaction1...

                final TransactionOutput transactionOutput0 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 0));
                Assert.assertNull(transactionOutput0); // Spent by Block2's Transaction1...

                final TransactionOutput transactionOutput1 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 1));
                Assert.assertNotNull(transactionOutput1);

                final TransactionOutput transactionOutput2 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction1.getHash(), 0));
                Assert.assertNotNull(transactionOutput2);

                final TransactionOutput transactionOutput3 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction1.getHash(), 1));
                Assert.assertNull(transactionOutput3);

                final TransactionOutput transactionOutput4 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction2.getHash(), 0));
                Assert.assertNotNull(transactionOutput4);

                final TransactionOutput transactionOutput5 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction2.getHash(), 1));
                Assert.assertNotNull(transactionOutput5);
            }

            // Action
            unspentTransactionOutputManager.removeBlockFromUtxoSet(block2, 2L);

            // Assert
            // Should be exactly the same as before Block2 was applied...
            final TransactionOutput coinbaseTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1CoinbaseTransaction.getHash(), 0));
            Assert.assertNull(coinbaseTransactionOutput); // Spent by Block1 Transaction1...

            final TransactionOutput transactionOutput0 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 0));
            Assert.assertNotNull(transactionOutput0);

            final TransactionOutput transactionOutput1 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 1));
            Assert.assertNotNull(transactionOutput1);
        }
    }

    @Test
    public void should_remove_created_utxos_and_restore_spent_outputs_when_removing_block_from_utxo_set_after_utxo_commit() throws Exception {
        // Setup
        final AddressInflater addressInflater = _masterInflater.getAddressInflater();
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, 2016L);

            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final Block block0 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK));

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.storeBlock(block0);
            }

            final Address address0 = addressInflater.fromPrivateKey(PrivateKey.createNewKey());
            final Address address1 = addressInflater.fromPrivateKey(PrivateKey.createNewKey());
            final Address address2 = addressInflater.fromPrivateKey(PrivateKey.createNewKey());

            final Block block1;
            final Block block2;
            final CoinbaseTransaction block1CoinbaseTransaction;
            final Transaction block1Transaction1;
            final Transaction block2CoinbaseTransaction;
            final Transaction block2Transaction1;
            final Transaction block2Transaction2;
            {
                final MutableBlock mutableBlock = new MutableBlock();
                { // Init Block Template...
                    mutableBlock.setVersion(Block.VERSION);
                    mutableBlock.setPreviousBlockHash(BlockHeader.GENESIS_BLOCK_HASH);
                    mutableBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
                    mutableBlock.setTimestamp(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
                    mutableBlock.setNonce(0L);
                }

                {
                    final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                    { // Init Transaction Input Template...
                        mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                        mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                    }

                    final MutableTransaction mutableTransaction = new MutableTransaction();
                    { // Init Transaction Template...
                        mutableTransaction.setVersion(Transaction.VERSION);
                        mutableTransaction.setLockTime(LockTime.MIN_TIMESTAMP);
                    }

                    { // Create Block1's CoinbaseTransaction; generates 50 BCH.
                        mutableTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInput(1L, ""));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address0, 50L * Transaction.SATOSHIS_PER_BITCOIN));
                        block1CoinbaseTransaction = mutableTransaction.asCoinbase();
                    }

                    { // Create Block1's second Transaction that spends Block1's Coinbase and creates two 25BCH outputs.
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        { // Configure TransactionInput to spent Block1's Coinbase Transaction.
                            mutableTransactionInput.setPreviousOutputTransactionHash(block1CoinbaseTransaction.getHash());
                            mutableTransactionInput.setPreviousOutputIndex(0);
                        }
                        mutableTransaction.addTransactionInput(mutableTransactionInput.asConst());
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 25L * Transaction.SATOSHIS_PER_BITCOIN));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address2, 25L * Transaction.SATOSHIS_PER_BITCOIN));
                        block1Transaction1 = mutableTransaction.asConst();
                    }

                    { // Create Block2's CoinbaseTransaction; generates 50 BCH.
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        mutableTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInput(2L, ""));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 50L * Transaction.SATOSHIS_PER_BITCOIN));
                        block2CoinbaseTransaction = mutableTransaction.asCoinbase();
                    }

                    { // Create Block2's second Transaction that spends Block1's second Transaction first TransactionOutput...
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        { // Configure TransactionInput to spent Block1's Coinbase Transaction.
                            mutableTransactionInput.setPreviousOutputTransactionHash(block1Transaction1.getHash());
                            mutableTransactionInput.setPreviousOutputIndex(0);
                        }
                        mutableTransaction.addTransactionInput(mutableTransactionInput.asConst());
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 12L * Transaction.SATOSHIS_PER_BITCOIN));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address2, 13L * Transaction.SATOSHIS_PER_BITCOIN));
                        block2Transaction1 = mutableTransaction.asConst();
                    }

                    { // Create Block2's third Transaction that spends Block2's second Transaction second TransactionOutput...
                        mutableTransaction.clearTransactionInputs();
                        mutableTransaction.clearTransactionOutputs();
                        { // Configure TransactionInput to spent Block1's Coinbase Transaction.
                            mutableTransactionInput.setPreviousOutputTransactionHash(block2Transaction1.getHash());
                            mutableTransactionInput.setPreviousOutputIndex(1);
                        }
                        mutableTransaction.addTransactionInput(mutableTransactionInput.asConst());
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address1, 10L * Transaction.SATOSHIS_PER_BITCOIN));
                        mutableTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address2, 3L * Transaction.SATOSHIS_PER_BITCOIN));
                        block2Transaction2 = mutableTransaction.asConst();
                    }
                }

                { // Assemble Block 1...
                    mutableBlock.addTransaction(block1CoinbaseTransaction);
                    mutableBlock.addTransaction(block1Transaction1);
                    block1 = mutableBlock.asConst();
                }

                { // Assemble Block 2...
                    mutableBlock.clearTransactions();
                    mutableBlock.addTransaction(block2CoinbaseTransaction);
                    mutableBlock.addTransaction(block2Transaction1);
                    mutableBlock.addTransaction(block2Transaction2);
                    block2 = mutableBlock.asConst();
                }
            }

            { // Store and sanity-check Block1 state...
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.storeBlock(block1);
                }
                unspentTransactionOutputManager.applyBlockToUtxoSet(block1, 1L, _fullNodeDatabaseManagerFactory);

                final TransactionOutput coinbaseTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1CoinbaseTransaction.getHash(), 0));
                Assert.assertNull(coinbaseTransactionOutput); // Spent by Block1 Transaction1...

                final TransactionOutput transactionOutput0 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 0));
                Assert.assertNotNull(transactionOutput0);

                final TransactionOutput transactionOutput1 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 1));
                Assert.assertNotNull(transactionOutput1);
            }

            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, CommitAsyncMode.BLOCK_UNTIL_COMPLETE); // Commit the UTXO set...

            { // Store and sanity-check Block2 state...
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.storeBlock(block2);
                }
                unspentTransactionOutputManager.applyBlockToUtxoSet(block2, 2L, _fullNodeDatabaseManagerFactory);

                final TransactionOutput coinbaseTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1CoinbaseTransaction.getHash(), 0));
                Assert.assertNull(coinbaseTransactionOutput); // Spent by Block1's Transaction1...

                final TransactionOutput transactionOutput0 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 0));
                Assert.assertNull(transactionOutput0); // Spent by Block2's Transaction1...

                final TransactionOutput transactionOutput1 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 1));
                Assert.assertNotNull(transactionOutput1);

                final TransactionOutput transactionOutput2 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction1.getHash(), 0));
                Assert.assertNotNull(transactionOutput2);

                final TransactionOutput transactionOutput3 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction1.getHash(), 1));
                Assert.assertNull(transactionOutput3);

                final TransactionOutput transactionOutput4 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction2.getHash(), 0));
                Assert.assertNotNull(transactionOutput4);

                final TransactionOutput transactionOutput5 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block2Transaction2.getHash(), 1));
                Assert.assertNotNull(transactionOutput5);
            }

            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, CommitAsyncMode.BLOCK_UNTIL_COMPLETE); // Commit the UTXO set...

            // Action
            unspentTransactionOutputManager.removeBlockFromUtxoSet(block2, 2L);
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, CommitAsyncMode.BLOCK_UNTIL_COMPLETE); // Commit the UTXO set...
            unspentTransactionOutputDatabaseManager.clearUncommittedUtxoSet();

            // Assert
            // Should be exactly the same as before Block2 was applied...
            final TransactionOutput coinbaseTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1CoinbaseTransaction.getHash(), 0));
            Assert.assertNull(coinbaseTransactionOutput); // Spent by Block1 Transaction1...

            final TransactionOutput transactionOutput0 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 0));
            Assert.assertNotNull(transactionOutput0);

            final TransactionOutput transactionOutput1 = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(new TransactionOutputIdentifier(block1Transaction1.getHash(), 1));
            Assert.assertNotNull(transactionOutput1);

            final Long committedBlockHeight = unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight();
            Assert.assertEquals(Long.valueOf(1L), committedBlockHeight);
        }
    }
}
