package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentSubBucket;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class UtxoCommitmentsMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public UtxoCommitmentsMessage fromBytes(final byte[] bytes) {
        final UtxoCommitmentsMessage utxoCommitmentsMessage = new UtxoCommitmentsMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.UTXO_COMMITMENTS);
        if (protocolMessageHeader == null) { return null; }

        final CompactVariableLengthInteger messageVersion = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! messageVersion.isCanonical()) { return null; }
        if (! Util.areEqual(UtxoCommitmentsMessage.VERSION, messageVersion.value)) { return null; }

        final CompactVariableLengthInteger commitmentCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! commitmentCount.isCanonical()) { return null; }
        if (commitmentCount.value > UtxoCommitmentsMessage.MAX_COMMITMENT_COUNT) { return null; }

        for (int i = 0; i < commitmentCount.value; ++i) {
            final Sha256Hash blockHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
            final Long blockHeight = byteArrayReader.readLong(4, Endian.LITTLE);
            final PublicKey commitmentPublicKey = PublicKey.fromBytes(byteArrayReader.readBytes(PublicKey.COMPRESSED_BYTE_COUNT));
            final Long totalByteCount = byteArrayReader.readLong(8, Endian.LITTLE);
            final int bucketCount = UtxoCommitment.BUCKET_COUNT;
            if (! commitmentPublicKey.isValid()) { return null; }

            final MutableList<UtxoCommitmentBucket> utxoCommitmentBuckets = new MutableList<>(bucketCount);
            for (int j = 0; j < bucketCount; ++j) {
                final ByteArray bucketPublicKeyBytes = ByteArray.wrap(byteArrayReader.readBytes(PublicKey.COMPRESSED_BYTE_COUNT));
                final PublicKey bucketPublicKey = PublicKey.fromBytes(bucketPublicKeyBytes);
                final Long bucketByteCount = byteArrayReader.readLong(8, Endian.LITTLE);
                final CompactVariableLengthInteger subBucketCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
                if (! subBucketCount.isCanonical()) { return null; }

                final MutableList<UtxoCommitmentSubBucket> subBuckets = new MutableList<>(subBucketCount.intValue());
                for (int k = 0; k < subBucketCount.value; ++k) {
                    final ByteArray subBucketPublicKeyBytes = ByteArray.wrap(byteArrayReader.readBytes(PublicKey.COMPRESSED_BYTE_COUNT));
                    final PublicKey subBucketPublicKey = PublicKey.fromBytes(subBucketPublicKeyBytes);
                    final Long subBucketByteCount = byteArrayReader.readLong(8, Endian.LITTLE);
                    if (bucketCount > UtxoCommitmentsMessage.MAX_SUB_BUCKET_COUNT) { return null; }

                    final UtxoCommitmentSubBucket subBucket = new UtxoCommitmentSubBucket(subBucketPublicKey, subBucketByteCount);
                    subBuckets.add(subBucket);
                }

                final UtxoCommitmentBucket utxoCommitmentBucket = new UtxoCommitmentBucket(bucketPublicKey, bucketByteCount, subBuckets);
                utxoCommitmentBuckets.add(utxoCommitmentBucket);

                if (byteArrayReader.didOverflow()) { return null; }
            }

            final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(blockHash, blockHeight, commitmentPublicKey, totalByteCount);
            utxoCommitmentsMessage.addUtxoCommitment(utxoCommitmentMetadata, utxoCommitmentBuckets);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return utxoCommitmentsMessage;
    }
}
