package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.module.node.manager.NodeFilter;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilderTests;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeBitcoinNode;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UtxoCommitmentDownloaderTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    protected List<UtxoCommitmentBucket> _inflateUtxoCommitmentBuckets(final String bucketResource, final String subBucketsResource) {
        final MutableList<UtxoCommitmentBucket> utxoCommitmentBuckets = new MutableList<>();

        final String bucketsCsv = IoUtil.getResource(bucketResource);
        final String subBucketsCsv = (subBucketsResource != null ? IoUtil.getResource(subBucketsResource) : "");

        int bucketIndex = 0;
        final String[] subBucketLines = subBucketsCsv.split("\n");
        for (final String line : bucketsCsv.split("\n")) {
            final String[] fields = line.split(",");
            final PublicKey bucketPublicKey = PublicKey.fromBytes(ByteArray.fromHexString(fields[0]));
            final Long bucketByteCount = Util.parseLong(fields[1]);

            final MutableList<MultisetBucket> subBuckets = new MutableList<>();
            if (subBucketsResource != null) {
                final String subBucketLine = subBucketLines[bucketIndex];
                final String[] subBucketFields = subBucketLine.split(",");
                for (int i = 0; i < subBucketFields.length / 2; ++i) {
                    final int fieldIndex = (i * 2);
                    final PublicKey subBucketPublicKey = PublicKey.fromBytes(ByteArray.fromHexString(subBucketFields[fieldIndex]));
                    final Long subBucketByteCount = Util.parseLong(subBucketFields[fieldIndex + 1]);

                    final MultisetBucket multisetBucket = new MultisetBucket(subBucketPublicKey, subBucketByteCount);
                    subBuckets.add(multisetBucket);
                }
            }

            final UtxoCommitmentBucket utxoCommitmentBucket = new UtxoCommitmentBucket(bucketPublicKey, bucketByteCount, subBuckets);
            utxoCommitmentBuckets.add(utxoCommitmentBucket);
            bucketIndex += 1;
        }
        return utxoCommitmentBuckets;
    }

    @Test
    public void should_attempt_to_download_latest_commitment() {
        // Setup
        final BitcoinNode bitcoinNode = new FakeBitcoinNode("1.2.3.4", 8333, null, null) {
            @Override
            public RequestId requestUtxoCommitments(final UtxoCommitmentsCallback callback) {
                final BitcoinNode bitcoinNode = this;
                final RequestId requestId = RequestId.wrap(1L);

                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final MutableList<UtxoCommitmentBreakdown> utxoCommitmentBreakdowns = new MutableList<>();

                        { // UTXO Commit 680000
                            final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(
                                Sha256Hash.fromHexString("00000000000000000040B37F904A9CBBA25A6D37AA313D4AE8C4C46589CF4C6E"),
                                680000L,
                                Sha256Hash.fromHexString("0D758E064E9B249257FDA7D270F01F9EA3A15110E5467FA174FB2F409C8BD897"),
                                3908702538L
                            );

                            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = _inflateUtxoCommitmentBuckets("/utxo/0D758E064E9B249257FDA7D270F01F9EA3A15110E5467FA174FB2F409C8BD897_buckets.csv", null);
                            utxoCommitmentBreakdowns.add(
                                new UtxoCommitmentBreakdown(utxoCommitmentMetadata, utxoCommitmentBuckets)
                            );
                        }

                        { // UTXO Commit 690000
                            final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(
                                Sha256Hash.fromHexString("000000000000000001993116A3D4D6431759CCECCE0E4F4C47E907E20D2BC535"),
                                690000L,
                                Sha256Hash.fromHexString("21515C522FC36D50D0EF0097210AD171CFBBCF83F65DAB1AE2F706B2F250440F"),
                                4456621219L
                            );

                            // TODO: The generated subBuckets used MiB, not MB. SubBuckets needs to be regenerated with 32MB max size.
                            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = _inflateUtxoCommitmentBuckets("/utxo/21515C522FC36D50D0EF0097210AD171CFBBCF83F65DAB1AE2F706B2F250440F_buckets.csv", "/utxo/21515C522FC36D50D0EF0097210AD171CFBBCF83F65DAB1AE2F706B2F250440F_sub_buckets.csv");
                            utxoCommitmentBreakdowns.add(
                                new UtxoCommitmentBreakdown(utxoCommitmentMetadata, utxoCommitmentBuckets)
                            );
                        }

                        callback.onResult(requestId, bitcoinNode, utxoCommitmentBreakdowns);
                    }
                })).start();

                return requestId;
            }
        };

        final UtxoCommitmentDownloader utxoCommitmentDownloader = new UtxoCommitmentDownloader(
            null,
            new BlockchainBuilderTests.FakeBitcoinNodeManager() {
                @Override
                public List<BitcoinNode> getNodes(final NodeFilter nodeFilter) {
                    return new ImmutableList<>(bitcoinNode);
                }
            },
            null,
            null,
            null
        );

        // Action
        final UtxoCommitmentDownloader.UtxoCommit utxoCommit = utxoCommitmentDownloader._calculateUtxoCommitToDownload();

        // Assert
        Assert.assertEquals(Long.valueOf(690000L), utxoCommit.utxoCommitment.blockHeight);
        Assert.assertEquals(Long.valueOf(4456621219L), utxoCommit.utxoCommitment.byteCount);
        Assert.assertEquals(Sha256Hash.fromHexString("000000000000000001993116A3D4D6431759CCECCE0E4F4C47E907E20D2BC535"), utxoCommit.utxoCommitment.blockHash);
        Assert.assertEquals(Sha256Hash.fromHexString("21515C522FC36D50D0EF0097210AD171CFBBCF83F65DAB1AE2F706B2F250440F"), utxoCommit.utxoCommitment.multisetHash);
    }
}
