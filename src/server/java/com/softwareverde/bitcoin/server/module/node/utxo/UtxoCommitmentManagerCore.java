package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentsMessage;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;

import java.io.File;

public class UtxoCommitmentManagerCore implements UtxoCommitmentManager {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final String _utxoCommitmentDirectory;

    public UtxoCommitmentManagerCore(final FullNodeDatabaseManagerFactory databaseManagerFactory, final String utxoCommitmentDirectory) {
        _databaseManagerFactory = databaseManagerFactory;
        _utxoCommitmentDirectory = utxoCommitmentDirectory;
    }

    @Override
    public List<UtxoCommitmentBreakdown> getAvailableUtxoCommitments() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final MutableList<UtxoCommitmentBreakdown> utxoCommitmentBreakdowns = new MutableList<>();

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT utxo_commitments.id, blocks.hash AS block_hash, utxo_commitments.hash AS commitment_hash, SUM(utxo_commitment_files.byte_count) AS commitment_byte_count FROM utxo_commitments INNER JOIN blocks ON blocks.id = utxo_commitments.block_id INNER JOIN utxo_commitment_files ON utxo_commitment_files.utxo_commit_id = utxo_commitments.id GROUP BY utxo_commitments.id ORDER BY blocks.block_height DESC LIMIT " + UtxoCommitmentsMessage.MAX_COMMITMENT_COUNT)
            );

            for (final Row row : rows) {
                final UtxoCommitmentId utxoCommitmentId = UtxoCommitmentId.wrap(row.getLong("id"));
                final Sha256Hash blockHash = Sha256Hash.wrap(row.getBytes("block_hash"));
                final Sha256Hash multisetHash = Sha256Hash.wrap(row.getBytes("commitment_hash"));
                final Long byteCount = row.getLong("commitment_byte_count");

                final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(blockHash, multisetHash, byteCount);

                final MutableList<UtxoCommitmentBucket> utxoCommitmentBuckets = new MutableList<>();
                final java.util.List<Row> bucketRows = databaseConnection.query(
                    new Query("SELECT utxo_commitment_buckets.public_key, utxo_commitment_buckets.`index`, SUM(utxo_commitment_files.byte_count) AS byte_count, COUNT(*) AS file_count FROM utxo_commitment_buckets INNER JOIN utxo_commitment_files ON (utxo_commitment_files.utxo_commitment_id = utxo_commitment_buckets.utxo_commitment_id AND utxo_commitment_files.bucket_index = utxo_commitment_buckets.`index`) WHERE utxo_commitment_buckets.utxo_commitment_id = ? GROUP BY utxo_commitment_buckets.`index`")
                        .setParameter(utxoCommitmentId)
                );
                for (final Row bucketRow : bucketRows) {
                    final PublicKey bucketPublicKey = PublicKey.fromBytes(bucketRow.getBytes("public_key"));
                    final Integer bucketIndex = bucketRow.getInteger("index");
                    final Long bucketByteCount = bucketRow.getLong("byte_count");
                    final boolean hasSubBuckets = (bucketRow.getInteger("file_count") > 1);

                    final MutableList<MultisetBucket> subBuckets = new MutableList<>();
                    if (hasSubBuckets) {
                        final java.util.List<Row> subBucketRows = databaseConnection.query(
                            new Query("SELECT public_key, byte_count FROM utxo_commitment_files WHERE utxo_commitment_id = ? AND bucket_index = ?")
                                .setParameter(utxoCommitmentId)
                                .setParameter(bucketIndex)
                        );
                        for (final Row subBucketRow : subBucketRows) {
                            final PublicKey subBucketPublicKey = PublicKey.fromBytes(subBucketRow.getBytes("public_key"));
                            final Long subBucketByteCount = subBucketRow.getLong("byte_count");

                            final MultisetBucket subBucket = new MultisetBucket(subBucketPublicKey, subBucketByteCount);
                            subBuckets.add(subBucket);
                        }
                    }

                    final UtxoCommitmentBucket utxoCommitmentBucket = new UtxoCommitmentBucket(bucketPublicKey, bucketByteCount, subBuckets);
                    utxoCommitmentBuckets.add(utxoCommitmentBucket);
                }
                final UtxoCommitmentBreakdown utxoCommitmentBreakdown = new UtxoCommitmentBreakdown(utxoCommitmentMetadata, utxoCommitmentBuckets);
                utxoCommitmentBreakdowns.add(utxoCommitmentBreakdown);
            }

            return utxoCommitmentBreakdowns;
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public ByteArray getUtxoCommitment(final PublicKey publicKey) {
        final File file = new File(_utxoCommitmentDirectory, publicKey.toString());
        if ( (! file.exists()) || (! file.canRead()) ) { return null; }

        return ByteArray.wrap(IoUtil.getFileContents(file));
    }
}
