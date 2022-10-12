package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.message.type.query.utxo.NodeSpecificUtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.util.BlockTestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.database.row.Row;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class UtxoCommitmentGeneratorIntegrationTests extends IntegrationTest {
    @Before @Override
    public void before() throws Exception {
        super.before();
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    protected static MutableCommittedUnspentTransactionOutput inflateCommittedUnspentTransactionOutput(final Sha256Hash transactionHash, final Integer outputIndex, final Long blockHeight, final Boolean isCoinbase, final Long amount, final String lockingScript) {
        final MutableCommittedUnspentTransactionOutput unspentTransactionOutput = new MutableCommittedUnspentTransactionOutput();
        unspentTransactionOutput.setTransactionHash(transactionHash);
        unspentTransactionOutput.setIndex(outputIndex);
        unspentTransactionOutput.setBlockHeight(blockHeight);
        unspentTransactionOutput.setIsCoinbase(isCoinbase);
        unspentTransactionOutput.setAmount(amount);
        unspentTransactionOutput.setLockingScript(ByteArray.fromHexString(lockingScript));

        return unspentTransactionOutput;
    }

    protected static void stageUnspentTransactionOutputs(final MutableList<CommittedUnspentTransactionOutput> unspentTransactionOutputs, final DatabaseConnectionFactory databaseConnectionFactory) throws Exception {
        final BatchRunner<CommittedUnspentTransactionOutput> batchRunner = new BatchRunner<>(1024);
        batchRunner.run(unspentTransactionOutputs, new BatchRunner.Batch<CommittedUnspentTransactionOutput>() {
            @Override
            public void run(final List<CommittedUnspentTransactionOutput> outputsBatch) throws Exception {
                final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO staged_utxo_commitment (transaction_hash, `index`, block_height, is_coinbase, amount, locking_script) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE block_height = VALUES(block_height), amount = VALUES(amount)");
                for (final CommittedUnspentTransactionOutput transactionOutput : outputsBatch) {
                    final Sha256Hash transactionHash = transactionOutput.getTransactionHash();
                    final Integer outputIndex = transactionOutput.getIndex();
                    final LockingScript lockingScript = transactionOutput.getLockingScript();
                    final ByteArray lockingScriptBytes = lockingScript.getBytes();

                    final Long blockHeight = transactionOutput.getBlockHeight();
                    final Boolean isCoinbase = transactionOutput.isCoinbase();

                    batchedInsertQuery.setParameter(transactionHash);
                    batchedInsertQuery.setParameter(outputIndex);
                    batchedInsertQuery.setParameter(blockHeight);
                    batchedInsertQuery.setParameter(isCoinbase);
                    batchedInsertQuery.setParameter(transactionOutput.getAmount());
                    batchedInsertQuery.setParameter(lockingScriptBytes);
                }

                try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    databaseConnection.executeSql(batchedInsertQuery);
                }
            }
        });
    }

    @Test
    public void should_deflate_and_inflate_utxo_commitment() throws Exception {
        // This test creates a UTXO commitment from some arbitrary UTXO set and then serializes it to a commitment file,
        //  then the commitment file is reinflated and its hash is recalculated to ensure the deflated and inflated hashes match.

        // Setup
        final MutableList<CommittedUnspentTransactionOutput> unspentTransactionOutputs = new MutableList<>();
        final int utxoCount = 500000; // NOTE: Average serialized UTXO is about 75-80 bytes, max file size is 32MB, so 500k UTXOs should create more than one file.
        for (int i = 0; i < utxoCount; ++i) {
            final Sha256Hash transactionHash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.integerToBytes(i)));
            final CommittedUnspentTransactionOutput transactionOutput = UtxoCommitmentGeneratorIntegrationTests.inflateCommittedUnspentTransactionOutput(transactionHash, (i % 10), 1L, (i == 0), (long) i, "76A914951F8D3754AE809204602F92EEF88D6BAF94DB9188AC");

            unspentTransactionOutputs.add(transactionOutput);
        }

        final BlockId blockId;
        final Sha256Hash blockHash;
        final BlockInflater blockInflater = new BlockInflater();
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK)));

                final Block block = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_1));
                blockId = blockHeaderDatabaseManager.storeBlockHeader(block);
                blockHash = block.getHash();
            }
        }

        UtxoCommitmentGeneratorIntegrationTests.stageUnspentTransactionOutputs(unspentTransactionOutputs, _databaseConnectionFactory);

        final UtxoCommitmentGenerator utxoCommitmentGenerator = new UtxoCommitmentGenerator(null, _utxoCommitmentStore.getUtxoDataDirectory()) {
            @Override
            protected Boolean _shouldAbort() {
                return false;
            }
        };

        // Action
        final MutableList<String> fileNames = new MutableList<>();
        final UtxoCommitment utxoCommitment;
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            utxoCommitment = utxoCommitmentGenerator._publishUtxoCommitment(blockId, blockHash, 1L, databaseManager);

            Assert.assertTrue(utxoCommitment.getFiles().getCount() > 1);

            for (final File file : utxoCommitment.getFiles()) {
                fileNames.add(file.getName());
            }
        }

        final MutableList<PublicKey> utxoBucketPublicKeys = new MutableList<>();
        final EcMultiset commitmentMultisetHash = new EcMultiset();
        final UtxoCommitmentLoader utxoCommitmentLoader = new UtxoCommitmentLoader();
        for (final File inputFile : utxoCommitment.getFiles()) {
            final UtxoCommitmentLoader.CalculateMultisetHashResult calculateMultisetHashResult = utxoCommitmentLoader.calculateMultisetHash(inputFile);
            final EcMultiset multisetHash = calculateMultisetHashResult.multisetHash;
            final PublicKey publicKey = multisetHash.getPublicKey();
            commitmentMultisetHash.add(publicKey);
            utxoBucketPublicKeys.add(publicKey);

            // Assert
            Assert.assertTrue(fileNames.contains(publicKey.toString()));
        }

        // Assert
        Assert.assertFalse(utxoCommitment.getFiles().isEmpty());
        Assert.assertNotEquals(utxoCommitment.getHash(), Sha256Hash.EMPTY_HASH);
        Assert.assertEquals(utxoCommitment.getHash(), commitmentMultisetHash.getHash());

        final Long maxBucketByteCount = (32L * ByteUtil.Unit.Binary.MEBIBYTES);
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final UtxoCommitmentManager utxoCommitmentManager = databaseManager.getUtxoCommitmentManager();
            final List<NodeSpecificUtxoCommitmentBreakdown> availableUtxoCommitments = utxoCommitmentManager.getAvailableUtxoCommitments();
            Assert.assertEquals(1L, availableUtxoCommitments.getCount());

            final NodeSpecificUtxoCommitmentBreakdown utxoCommitmentBreakdown = availableUtxoCommitments.get(0);
            Assert.assertEquals(128, utxoCommitmentBreakdown.getBuckets().getCount());

            for (int i = 0; i < 128; ++i) {
                final UtxoCommitmentBucket utxoCommitmentBucket = utxoCommitmentBreakdown.getBuckets().get(i);
                final PublicKey expectedPublicKey = utxoCommitmentBucket.getPublicKey();
                final PublicKey bucketPublicKey = utxoBucketPublicKeys.get(i);

                Assert.assertEquals(expectedPublicKey, bucketPublicKey);

                final Long bucketByteCount = utxoCommitmentBucket.getByteCount();
                final boolean shouldHaveSubBuckets = (bucketByteCount > maxBucketByteCount);
                final boolean hasSubBuckets = utxoCommitmentBucket.hasSubBuckets();
                Assert.assertEquals(shouldHaveSubBuckets, hasSubBuckets);
            }
        }
    }

    @Test
    public void should_always_create_128_buckets_even_if_unused() throws Exception {
        // Setup
        final MutableList<CommittedUnspentTransactionOutput> unspentTransactionOutputs = new MutableList<>();
        final int utxoCount = 32; // Guaranteed to be less than 128 buckets.
        for (int i = 0; i < utxoCount; ++i) {
            final Sha256Hash transactionHash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.integerToBytes(i)));
            final CommittedUnspentTransactionOutput transactionOutput = UtxoCommitmentGeneratorIntegrationTests.inflateCommittedUnspentTransactionOutput(transactionHash, (i % 10), 1L, (i == 0), (long) i, "76A914951F8D3754AE809204602F92EEF88D6BAF94DB9188AC");

            unspentTransactionOutputs.add(transactionOutput);
        }

        final BlockId blockId;
        final Sha256Hash blockHash;
        final BlockInflater blockInflater = new BlockInflater();
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK)));

                final Block block = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_1));
                blockId = blockHeaderDatabaseManager.storeBlockHeader(block);
                blockHash = block.getHash();
            }
        }

        UtxoCommitmentGeneratorIntegrationTests.stageUnspentTransactionOutputs(unspentTransactionOutputs, _databaseConnectionFactory);

        final UtxoCommitmentGenerator utxoCommitmentGenerator = new UtxoCommitmentGenerator(null, _utxoCommitmentStore.getUtxoDataDirectory()) {
            @Override
            protected Boolean _shouldAbort() {
                return false;
            }
        };

        // Action
        final MutableList<String> fileNames = new MutableList<>();
        final UtxoCommitment utxoCommitment;
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            utxoCommitment = utxoCommitmentGenerator._publishUtxoCommitment(blockId, blockHash, 1L, databaseManager);

            Assert.assertTrue(utxoCommitment.getFiles().getCount() > 1);

            for (final File file : utxoCommitment.getFiles()) {
                fileNames.add(file.getName());
            }
        }

        final EcMultiset commitmentMultisetHash = new EcMultiset();
        final UtxoCommitmentLoader utxoCommitmentLoader = new UtxoCommitmentLoader();
        for (final File inputFile : utxoCommitment.getFiles()) {
            final UtxoCommitmentLoader.CalculateMultisetHashResult calculateMultisetHashResult = utxoCommitmentLoader.calculateMultisetHash(inputFile);
            final EcMultiset multisetHash = calculateMultisetHashResult.multisetHash;
            final PublicKey publicKey = multisetHash.getPublicKey();
            commitmentMultisetHash.add(publicKey);

            // Assert
            Assert.assertTrue(fileNames.contains(publicKey.toString()));
        }

        // Assert
        Assert.assertEquals(utxoCommitment.getHash(), commitmentMultisetHash.getHash());
        Assert.assertEquals(128, fileNames.getCount());
    }

    protected static Block makeFakeBlock(final Sha256Hash previousBlockHash, final Transaction coinbaseTransaction) {
        final MutableBlock fakeBlock = BlockTestUtil.createBlock();
        fakeBlock.setPreviousBlockHash(previousBlockHash);
        fakeBlock.addTransaction(coinbaseTransaction);
        return fakeBlock;
    }

    @Test
    public void should_use_higher_block_height_for_duplicate_transactions() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final TransactionInflater transactionInflater = new TransactionInflater();
                final BlockInflater blockInflater = new BlockInflater();

                blockDatabaseManager.storeBlock(blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK)));

                // Transaction: E3BF3D07D4B0375638D5F1DB5255FE07BA2C4CB067CD81B84EE974B6585FB468; Heights: 91722 & 91880
                final Transaction transaction1 = transactionInflater.fromBytes(ByteArray.fromHexString("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF060456720E1B00FFFFFFFF0100F2052A01000000434104124B212F5416598A92CCEC88819105179DCB2550D571842601492718273FE0F2179A9695096BFF94CD99DCCCDEA7CD9BD943BFCA8FEA649CAC963411979A33E9AC00000000"));
                final Block block1 = UtxoCommitmentGeneratorIntegrationTests.makeFakeBlock(Block.GENESIS_BLOCK_HASH, transaction1);
                blockDatabaseManager.storeBlock(block1);
                final Block block2 = UtxoCommitmentGeneratorIntegrationTests.makeFakeBlock(block1.getHash(), transaction1);
                blockDatabaseManager.storeBlock(block2);

                // Transaction: D5D27987D2A3DFC724E359870C6644B40E497BDC0589A033220FE15429D88599; Heights: 91812 & 91842
                final Transaction transaction2 = transactionInflater.fromBytes(ByteArray.fromHexString("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF060456720E1B00FFFFFFFF0100F2052A010000004341046896ECFC449CB8560594EB7F413F199DEB9B4E5D947A142E7DC7D2DE0B811B8E204833EA2A2FD9D4C7B153A8CA7661D0A0B7FC981DF1F42F55D64B26B3DA1E9CAC00000000"));
                final Block block3 = UtxoCommitmentGeneratorIntegrationTests.makeFakeBlock(block2.getHash(), transaction2);
                blockDatabaseManager.storeBlock(block3);
                final Block block4 = UtxoCommitmentGeneratorIntegrationTests.makeFakeBlock(block3.getHash(), transaction2);
                blockDatabaseManager.storeBlock(block4);
            }
        }

        final UtxoCommitmentGenerator utxoCommitmentGenerator = new UtxoCommitmentGenerator(null, _utxoCommitmentStore.getUtxoDataDirectory(), 0) {
            @Override
            protected Boolean _shouldAbort() {
                return false;
            }
        };

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            // Action
            utxoCommitmentGenerator._updateStagedCommitment(databaseManager);

            // Assert
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM staged_utxo_commitment ORDER BY block_height ASC"));
            Assert.assertEquals(2, rows.size());
            Assert.assertEquals(Long.valueOf(2L), rows.get(0).getLong("block_height"));
            Assert.assertEquals(Long.valueOf(4L), rows.get(1).getLong("block_height"));
        }
    }
}
