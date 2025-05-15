package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentSubBucket;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class UtxoCommitmentsMessage extends BitcoinProtocolMessage {
    public static final Integer VERSION = 1;
    public static final Integer MAX_COMMITMENT_COUNT = 32;
    public static final Integer MAX_SUB_BUCKET_COUNT = 128;

    protected final MutableList<NodeSpecificUtxoCommitmentBreakdown> _commitments = new MutableArrayList<>();
    protected ByteArray _cachedBytes = null;

    public UtxoCommitmentsMessage() {
        super(MessageType.UTXO_COMMITMENTS);
    }

    public void addUtxoCommitment(final UtxoCommitmentMetadata utxoCommitmentParam, final List<UtxoCommitmentBucket> utxoCommitmentBuckets) {
        if (_commitments.getCount() >= MAX_COMMITMENT_COUNT) { return; }

        _commitments.add(new NodeSpecificUtxoCommitmentBreakdown(utxoCommitmentParam, utxoCommitmentBuckets));
        _cachedBytes = null;
    }

    public List<NodeSpecificUtxoCommitmentBreakdown> getUtxoCommitments() {
        return _commitments;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArray cachedBytes = _cachedBytes;
        if (cachedBytes != null) {
            return cachedBytes;
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(UtxoCommitmentsMessage.VERSION));

        final int commitmentCount = _commitments.getCount();
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(commitmentCount));
        for (final NodeSpecificUtxoCommitmentBreakdown utxoCommitment : _commitments) {
            final UtxoCommitmentMetadata utxoCommitmentMetadata = utxoCommitment.getMetadata();
            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = utxoCommitment.getBuckets();
            final PublicKey compressedPublicKey = utxoCommitmentMetadata.publicKey.compress();

            byteArrayBuilder.appendBytes(utxoCommitmentMetadata.blockHash, Endian.LITTLE);
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(utxoCommitmentMetadata.blockHeight), Endian.LITTLE); // 4 bytes.
            byteArrayBuilder.appendBytes(compressedPublicKey);
            byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(utxoCommitmentMetadata.byteCount));

            final int utxoCommitmentBucketCount = utxoCommitmentBuckets.getCount();
            if (utxoCommitmentBucketCount != UtxoCommitment.BUCKET_COUNT) { return null; }

            for (final UtxoCommitmentBucket utxoCommitmentBucket : utxoCommitmentBuckets) {
                final PublicKey utxoCommitmentBucketPublicKey = utxoCommitmentBucket.getPublicKey();
                final Long byteCount = utxoCommitmentBucket.getByteCount();

                byteArrayBuilder.appendBytes(utxoCommitmentBucketPublicKey);
                byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(byteCount));

                final List<UtxoCommitmentSubBucket> subBuckets = utxoCommitmentBucket.getSubBuckets();
                final int subBucketCount = subBuckets.getCount();
                byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(subBucketCount));
                for (final UtxoCommitmentSubBucket subBucket : subBuckets) {
                    final PublicKey subBucketPublicKey = subBucket.getPublicKey();
                    final Long subBucketByteCount = subBucket.getByteCount();

                    byteArrayBuilder.appendBytes(subBucketPublicKey);
                    byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(subBucketByteCount));
                }
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
