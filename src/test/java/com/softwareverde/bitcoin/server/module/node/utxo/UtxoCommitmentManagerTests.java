package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.UtxoCommitmentDownloaderTests;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UtxoCommitmentManagerTests extends IntegrationTest {
    @Before @Override
    public void before() throws Exception {
        super.before();
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_get_available_utxo_commitments() throws Exception {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final BlockHeader block0 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final BlockHeader block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final BlockHeader block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));

        final MutableList<UtxoCommitmentBreakdown> utxoCommitmentBreakdowns = new MutableList<>();
        { // UTXO Commit 680000
            final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(
                block1.getHash(),
                1L,
                Sha256Hash.fromHexString("0D758E064E9B249257FDA7D270F01F9EA3A15110E5467FA174FB2F409C8BD897"),
                3908702538L
            );

            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = UtxoCommitmentDownloaderTests.inflateUtxoCommitmentBuckets("/utxo/0D758E064E9B249257FDA7D270F01F9EA3A15110E5467FA174FB2F409C8BD897_buckets.csv", null);
            utxoCommitmentBreakdowns.add(
                new UtxoCommitmentBreakdown(utxoCommitmentMetadata, utxoCommitmentBuckets)
            );
        }

        { // UTXO Commit 690000
            final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(
                block2.getHash(),
                2L,
                Sha256Hash.fromHexString("21515C522FC36D50D0EF0097210AD171CFBBCF83F65DAB1AE2F706B2F250440F"),
                4456621219L
            );

            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = UtxoCommitmentDownloaderTests.inflateUtxoCommitmentBuckets("/utxo/21515C522FC36D50D0EF0097210AD171CFBBCF83F65DAB1AE2F706B2F250440F_buckets.csv", "/utxo/21515C522FC36D50D0EF0097210AD171CFBBCF83F65DAB1AE2F706B2F250440F_sub_buckets.csv");
            utxoCommitmentBreakdowns.add(
                new UtxoCommitmentBreakdown(utxoCommitmentMetadata, utxoCommitmentBuckets)
            );
        }

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(block0);
                blockHeaderDatabaseManager.storeBlockHeader(block1);
                blockHeaderDatabaseManager.storeBlockHeader(block2);
            }

            for (final UtxoCommitmentBreakdown utxoCommitmentBreakdown : utxoCommitmentBreakdowns) {
                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(utxoCommitmentBreakdown.commitment.blockHash);

                final UtxoCommitmentGenerator utxoCommitmentGenerator = new UtxoCommitmentGenerator(_fullNodeDatabaseManagerFactory, _utxoCommitmentStore.getUtxoDataDirectory());
                final UtxoCommitmentId utxoCommitmentId = utxoCommitmentGenerator._createUtxoCommitment(blockId, databaseManager);
                utxoCommitmentGenerator._setUtxoCommitmentHash(utxoCommitmentId, utxoCommitmentBreakdown.commitment.multisetHash, databaseManager);

                int bucketIndex = 0;
                for (final UtxoCommitmentBucket utxoCommitmentBucket : utxoCommitmentBreakdown.buckets) {
                    final Long bucketId = utxoCommitmentGenerator._createUtxoCommitmentBucket(utxoCommitmentId, bucketIndex, utxoCommitmentBucket.getPublicKey(), databaseManager);

                    if (utxoCommitmentBucket.hasSubBuckets()) {
                        int subBucketIndex = 0;
                        for (final MultisetBucket utxoCommitmentSubBucket : utxoCommitmentBucket.getSubBuckets()) {
                            utxoCommitmentGenerator._createUtxoCommitmentFile(bucketId, subBucketIndex, utxoCommitmentSubBucket.getPublicKey(), 415025, utxoCommitmentSubBucket.getByteCount(), databaseManager);
                            subBucketIndex += 1;
                        }
                    }
                    else {
                        utxoCommitmentGenerator._createUtxoCommitmentFile(bucketId, 0, utxoCommitmentBucket.getPublicKey(), 415025, utxoCommitmentBucket.getByteCount(), databaseManager);
                    }

                    bucketIndex += 1;
                }
            }
        }

        // Action
        final List<UtxoCommitmentBreakdown> retrievedUtxoCommitmentBreakdowns;
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final UtxoCommitmentManager utxoCommitmentManager = databaseManager.getUtxoCommitmentManager();

            retrievedUtxoCommitmentBreakdowns = utxoCommitmentManager.getAvailableUtxoCommitments();
        }

        // Assert
        Assert.assertEquals(utxoCommitmentBreakdowns.getCount(), retrievedUtxoCommitmentBreakdowns.getCount());
        for (final UtxoCommitmentBreakdown utxoCommitmentBreakdown : utxoCommitmentBreakdowns) {
            Assert.assertTrue(retrievedUtxoCommitmentBreakdowns.contains(utxoCommitmentBreakdown));
        }
    }
}
