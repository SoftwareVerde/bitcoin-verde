package com.softwareverde.bitcoin.server.module.node.database.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoDatabaseSubBucket;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;

import java.util.Comparator;
import java.util.HashMap;

public class UtxoCommitmentDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    public static Sha256Hash calculateEcMultisetHash(final PublicKey publicKey) {
        final EcMultiset ecMultiset = new EcMultiset(publicKey);
        return ecMultiset.getHash();
    }

    protected Long _createUtxoCommitmentFile(final Long bucketId, final Integer subBucketIndex, final PublicKey subBucketPublicKey, final Integer subBucketUtxoCount, final Long subBucketByteCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        return databaseConnection.executeSql(
            new Query("INSERT INTO utxo_commitment_files (utxo_commitment_bucket_id, sub_bucket_index, public_key, utxo_count, byte_count) VALUES (?, ?, ?, ?, ?)")
                .setParameter(bucketId)
                .setParameter(subBucketIndex)
                .setParameter(subBucketPublicKey)
                .setParameter(subBucketUtxoCount)
                .setParameter(subBucketByteCount)
        );
    }

    protected void _setUtxoCommitmentHash(final UtxoCommitmentId utxoCommitmentId, final Sha256Hash hash, final PublicKey publicKey) throws DatabaseException {
        final PublicKey compressedPublicKey = publicKey.compress();
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE utxo_commitments SET hash = ?, public_key = ? WHERE id = ?")
                .setParameter(hash)
                .setParameter(compressedPublicKey)
                .setParameter(utxoCommitmentId)
        );
    }

    protected UtxoCommitmentId _createUtxoCommitment(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long utxoCommitId = databaseConnection.executeSql(
            new Query("INSERT INTO utxo_commitments (block_id) VALUES (?)")
                .setParameter(blockId)
        );

        return UtxoCommitmentId.wrap(utxoCommitId);
    }

    protected Long _createUtxoCommitmentBucket(final UtxoCommitmentId utxoCommitmentId, final Integer bucketIndex, final PublicKey publicKey) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        return databaseConnection.executeSql(
            new Query("INSERT INTO utxo_commitment_buckets (utxo_commitment_id, `index`, public_key) VALUES (?, ?, ?)")
                .setParameter(utxoCommitmentId)
                .setParameter(bucketIndex)
                .setParameter(publicKey)
        );
    }

    public UtxoCommitmentDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public UtxoCommitmentId createUtxoCommitment(final BlockId blockId) throws DatabaseException {
        return _createUtxoCommitment(blockId);
    }

    public Long createUtxoCommitmentBucket(final UtxoCommitmentId utxoCommitmentId, final Integer bucketIndex, final PublicKey publicKey) throws DatabaseException {
        return _createUtxoCommitmentBucket(utxoCommitmentId, bucketIndex, publicKey);
    }

    public Long createUtxoCommitmentFile(final Long bucketId, final Integer subBucketIndex, final PublicKey subBucketPublicKey, final Integer subBucketUtxoCount, final Long subBucketByteCount) throws DatabaseException {
        return _createUtxoCommitmentFile(bucketId, subBucketIndex, subBucketPublicKey, subBucketUtxoCount, subBucketByteCount);
    }

    public void setUtxoCommitmentHash(final UtxoCommitmentId utxoCommitmentId, final Sha256Hash hash, final PublicKey publicKey) throws DatabaseException {
        _setUtxoCommitmentHash(utxoCommitmentId, hash, publicKey);
    }

    public Sha256Hash getUtxoCommitmentHash(final UtxoCommitmentId utxoCommitmentId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM utxo_commitments WHERE id = ?")
                .setParameter(utxoCommitmentId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.wrap(row.getBytes("hash"));
    }

    public void storeUtxoCommitment(final UtxoCommitmentMetadata utxoCommitmentMetadata, final List<UtxoDatabaseSubBucket> utxoCommitmentFiles) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(utxoCommitmentMetadata.blockHash);
        final UtxoCommitmentId utxoCommitmentId = _createUtxoCommitment(blockId);
        final Sha256Hash ecMultisetHash = UtxoCommitmentDatabaseManager.calculateEcMultisetHash(utxoCommitmentMetadata.publicKey);
        _setUtxoCommitmentHash(utxoCommitmentId, ecMultisetHash, utxoCommitmentMetadata.publicKey);

        final HashMap<Integer, EcMultiset> bucketHashes = new HashMap<>();
        final HashMap<Integer, MutableList<UtxoDatabaseSubBucket>> bucketFiles = new HashMap<>();
        for (final UtxoDatabaseSubBucket subBucket : utxoCommitmentFiles) {
            bucketFiles.putIfAbsent(subBucket.bucketIndex, new MutableList<>());
            bucketHashes.putIfAbsent(subBucket.bucketIndex, new EcMultiset());

            final MutableList<UtxoDatabaseSubBucket> subBuckets = bucketFiles.get(subBucket.bucketIndex);
            subBuckets.add(subBucket);

            final EcMultiset bucketHash = bucketHashes.get(subBucket.bucketIndex);
            bucketHash.add(subBucket.subBucketPublicKey);
        }
        for (final MutableList<UtxoDatabaseSubBucket> subBuckets : bucketFiles.values()) {
            subBuckets.sort(new Comparator<UtxoDatabaseSubBucket>() {
                @Override
                public int compare(final UtxoDatabaseSubBucket subBucket0, final UtxoDatabaseSubBucket subBucket1) {
                    return subBucket0.subBucketIndex.compareTo(subBucket1.subBucketIndex);
                }
            });
        }

        for (final Integer bucketIndex : bucketHashes.keySet()) {
            final EcMultiset bucketHash = bucketHashes.get(bucketIndex);
            final List<UtxoDatabaseSubBucket> subBuckets = bucketFiles.get(bucketIndex);

            final PublicKey bucketPublicKey = bucketHash.getPublicKey();

            final Long bucketId = _createUtxoCommitmentBucket(utxoCommitmentId, bucketIndex, bucketPublicKey);
            for (final UtxoDatabaseSubBucket subBucket : subBuckets) {
                _createUtxoCommitmentFile(bucketId, subBucket.subBucketIndex, subBucket.subBucketPublicKey, subBucket.subBucketUtxoCount, subBucket.subBucketByteCount);
            }
        }
    }
}
