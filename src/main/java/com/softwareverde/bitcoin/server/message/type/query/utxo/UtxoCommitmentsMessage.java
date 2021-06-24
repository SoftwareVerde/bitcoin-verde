package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class UtxoCommitmentsMessage extends BitcoinProtocolMessage {
    public static Integer VERSION = 1;
    public static final Integer MAX_COMMITMENT_COUNT = 32;
    public static final Integer MAX_SUB_BUCKET_COUNT = 128;

    protected final MutableList<UtxoCommitmentBreakdown> _commitments = new MutableList<>();
    protected ByteArray _cachedBytes = null;

    public UtxoCommitmentsMessage() {
        super(MessageType.UTXO_COMMITMENTS);
    }

    public void addUtxoCommitment(final UtxoCommitmentMetadata utxoCommitmentParam, final List<UtxoCommitmentBucket> utxoCommitmentBuckets) {
        if (_commitments.getCount() >= MAX_COMMITMENT_COUNT) { return; }

        _commitments.add(new UtxoCommitmentBreakdown(utxoCommitmentParam, utxoCommitmentBuckets));
        _cachedBytes = null;
    }

    public List<UtxoCommitmentBreakdown> getUtxoCommitments() {
        return _commitments;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArray cachedBytes = _cachedBytes;
        if (cachedBytes != null) {
            return cachedBytes;
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(UtxoCommitmentsMessage.VERSION));

        final int commitmentCount = _commitments.getCount();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(commitmentCount));
        for (final UtxoCommitmentBreakdown utxoCommitment : _commitments) {
            final UtxoCommitmentMetadata utxoCommitmentMetadata = utxoCommitment.commitment;
            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = utxoCommitment.buckets;
            final PublicKey compressedPublicKey = utxoCommitmentMetadata.publicKey.compress();

            byteArrayBuilder.appendBytes(utxoCommitmentMetadata.blockHash, Endian.LITTLE);
            byteArrayBuilder.appendBytes(ByteUtil.longToBytes(utxoCommitmentMetadata.blockHeight), Endian.LITTLE);
            byteArrayBuilder.appendBytes(compressedPublicKey, Endian.LITTLE);
            byteArrayBuilder.appendBytes(ByteUtil.longToBytes(utxoCommitmentMetadata.byteCount), Endian.LITTLE);

            int bucketIndex = 0;
            for (final UtxoCommitmentBucket utxoCommitmentBucket : utxoCommitmentBuckets) {
                if (bucketIndex > UtxoCommitment.BUCKET_COUNT) { break; }

                final PublicKey utxoCommitmentBucketPublicKey = utxoCommitmentBucket.getPublicKey();
                final Long byteCount = utxoCommitmentBucket.getByteCount();

                byteArrayBuilder.appendBytes(utxoCommitmentBucketPublicKey);
                byteArrayBuilder.appendBytes(ByteUtil.longToBytes(byteCount), Endian.LITTLE);

                final List<MultisetBucket> subBuckets = utxoCommitmentBucket.getSubBuckets();
                final int subBucketCount = subBuckets.getCount();
                byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(subBucketCount));
                for (final MultisetBucket subBucket : subBuckets) {
                    final PublicKey subBucketPublicKey = subBucket.getPublicKey();
                    final Long subBucketByteCount = subBucket.getByteCount();

                    byteArrayBuilder.appendBytes(subBucketPublicKey);
                    byteArrayBuilder.appendBytes(ByteUtil.longToBytes(subBucketByteCount), Endian.LITTLE);
                }
                bucketIndex += 1;
            }
        }

        _cachedBytes = byteArrayBuilder;
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final ByteArray cachedBytes = _cachedBytes;
        if (cachedBytes != null) {
            return cachedBytes.getByteCount();
        }

        final ByteArray payload = _getPayload();
        _cachedBytes = payload;
        return payload.getByteCount();
    }
}
