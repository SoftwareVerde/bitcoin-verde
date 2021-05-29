package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.MultisetHash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.util.HashUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

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
        final BlockInflater blockInflater = new BlockInflater();
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK)));
                blockId = blockHeaderDatabaseManager.storeBlockHeader(blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.BLOCK_1)));
            }
        }

        final BatchRunner<CommittedUnspentTransactionOutput> batchRunner = new BatchRunner<>(1024);
        batchRunner.run(unspentTransactionOutputs, new BatchRunner.Batch<CommittedUnspentTransactionOutput>() {
            @Override
            public void run(final List<CommittedUnspentTransactionOutput> outputsBatch) throws Exception {
                final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO staged_unspent_transaction_output_commitment (transaction_hash, `index`, block_height, is_coinbase, amount, locking_script) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)");
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

                try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    databaseConnection.executeSql(batchedInsertQuery);
                }
            }
        });

        final String outputDirectory = Files.createTempDirectory("utxo").toFile().getAbsolutePath();
        final UtxoCommitmentGenerator utxoCommitmentGenerator = new UtxoCommitmentGenerator(null, outputDirectory);

        // Action
        final MutableList<String> fileNames = new MutableList<>();
        final UtxoCommitment utxoCommitment;
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            utxoCommitment = utxoCommitmentGenerator._publishUtxoCommitment(blockId, 1L, databaseManager);

            Assert.assertTrue(utxoCommitment.getFiles().getCount() > 1);

            for (final File file : utxoCommitment.getFiles()) {
                fileNames.add(file.getName());
            }
        }

        final MultisetHash commitmentMultisetHash = new MultisetHash();
        final UtxoCommitmentLoader utxoCommitmentLoader = new UtxoCommitmentLoader();
        for (final File inputFile : utxoCommitment.getFiles()) {
            final MultisetHash multisetHash = utxoCommitmentLoader.calculateMultisetHash(inputFile);
            final PublicKey publicKey = multisetHash.getPublicKey();
            commitmentMultisetHash.add(publicKey);

            // Assert
            Assert.assertTrue(fileNames.contains(publicKey.toString()));
        }

        // Assert
        Assert.assertEquals(utxoCommitment.getHash(), commitmentMultisetHash.getHash());
    }
}
