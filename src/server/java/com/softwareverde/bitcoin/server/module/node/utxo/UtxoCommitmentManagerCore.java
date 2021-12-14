package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentSubBucket;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.message.type.query.utxo.NodeSpecificUtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentsMessage;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;

public class UtxoCommitmentManagerCore implements UtxoCommitmentManager {
    protected final DatabaseConnection _databaseConnection;
    protected final UtxoCommitmentStore _utxoCommitmentStore;

    public UtxoCommitmentManagerCore(final DatabaseConnection databaseConnection, final UtxoCommitmentStore utxoCommitmentStore) {
        _databaseConnection = databaseConnection;
        _utxoCommitmentStore = utxoCommitmentStore;
    }

    @Override
    public List<NodeSpecificUtxoCommitmentBreakdown> getAvailableUtxoCommitments() throws DatabaseException {
        final MutableList<NodeSpecificUtxoCommitmentBreakdown> utxoCommitmentBreakdowns = new MutableList<>();

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT utxo_commitments.id, blocks.hash AS block_hash, blocks.block_height, utxo_commitments.public_key AS public_key, SUM(utxo_commitment_files.byte_count) AS commitment_byte_count FROM utxo_commitments INNER JOIN blocks ON blocks.id = utxo_commitments.block_id INNER JOIN utxo_commitment_buckets ON utxo_commitment_buckets.utxo_commitment_id = utxo_commitments.id INNER JOIN utxo_commitment_files ON utxo_commitment_files.utxo_commitment_bucket_id = utxo_commitment_buckets.id GROUP BY utxo_commitments.id ORDER BY blocks.block_height DESC LIMIT " + UtxoCommitmentsMessage.MAX_COMMITMENT_COUNT)
        );

        for (final Row row : rows) {
            final UtxoCommitmentId utxoCommitmentId = UtxoCommitmentId.wrap(row.getLong("id"));
            final Sha256Hash blockHash = Sha256Hash.wrap(row.getBytes("block_hash"));
            final Long blockHeight = row.getLong("block_height");
            final PublicKey multisetPublicKey = PublicKey.fromBytes(row.getBytes("public_key"));
            final Long byteCount = row.getLong("commitment_byte_count");

            final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(blockHash, blockHeight, multisetPublicKey, byteCount);

            final MutableList<UtxoCommitmentBucket> utxoCommitmentBuckets = new MutableList<>();
            final java.util.List<Row> bucketRows = _databaseConnection.query(
                new Query("SELECT utxo_commitment_buckets.id, utxo_commitment_buckets.public_key, utxo_commitment_buckets.`index`, SUM(utxo_commitment_files.byte_count) AS byte_count, COUNT(*) AS file_count FROM utxo_commitment_buckets INNER JOIN utxo_commitment_files ON utxo_commitment_files.utxo_commitment_bucket_id = utxo_commitment_buckets.id WHERE utxo_commitment_buckets.utxo_commitment_id = ? GROUP BY utxo_commitment_buckets.`index` ORDER BY utxo_commitment_buckets.`index` ASC")
                    .setParameter(utxoCommitmentId)
            );
            for (final Row bucketRow : bucketRows) {
                final Long bucketId = bucketRow.getLong("id");
                final PublicKey bucketPublicKey = PublicKey.fromBytes(bucketRow.getBytes("public_key"));
                // final Integer bucketIndex = bucketRow.getInteger("index");
                final Long bucketByteCount = bucketRow.getLong("byte_count");
                final boolean hasSubBuckets = (bucketRow.getInteger("file_count") > 1);

                final MutableList<UtxoCommitmentSubBucket> subBuckets = new MutableList<>();
                if (hasSubBuckets) {
                    final java.util.List<Row> subBucketRows = _databaseConnection.query(
                        new Query("SELECT public_key, byte_count FROM utxo_commitment_files WHERE utxo_commitment_bucket_id = ? ORDER BY sub_bucket_index ASC")
                            .setParameter(bucketId)
                    );
                    for (final Row subBucketRow : subBucketRows) {
                        final PublicKey subBucketPublicKey = PublicKey.fromBytes(subBucketRow.getBytes("public_key"));
                        final Long subBucketByteCount = subBucketRow.getLong("byte_count");

                        final UtxoCommitmentSubBucket subBucket = new UtxoCommitmentSubBucket(subBucketPublicKey, subBucketByteCount);
                        subBuckets.add(subBucket);
                    }
                }

                final UtxoCommitmentBucket utxoCommitmentBucket = new UtxoCommitmentBucket(bucketPublicKey, bucketByteCount, subBuckets);
                utxoCommitmentBuckets.add(utxoCommitmentBucket);
            }
            final NodeSpecificUtxoCommitmentBreakdown utxoCommitmentBreakdown = new NodeSpecificUtxoCommitmentBreakdown(utxoCommitmentMetadata, utxoCommitmentBuckets);
            utxoCommitmentBreakdowns.add(utxoCommitmentBreakdown);
        }

        return utxoCommitmentBreakdowns;
    }

    @Override
    public ByteArray getUtxoCommitment(final PublicKey publicKey) {
        return _utxoCommitmentStore.getUtxoCommitment(publicKey);
    }

    @Override
    public UtxoCommitmentId getUtxoCommitmentId(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM utxo_commitments WHERE block_id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return UtxoCommitmentId.wrap(row.getLong("id"));
    }
}
