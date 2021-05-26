package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class UtxoCommitmentsMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public UtxoCommitmentsMessage fromBytes(final byte[] bytes) {
        final UtxoCommitmentsMessage utxoCommitmentsMessage = new UtxoCommitmentsMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.UTXO_COMMITMENTS);
        if (protocolMessageHeader == null) { return null; }

        final Long commitmentCount = byteArrayReader.readVariableLengthInteger();
        if (commitmentCount > UtxoCommitmentsMessage.MAX_COMMITMENT_COUNT) { return null; }

        for (int i = 0; i < commitmentCount; ++i) {
            final Sha256Hash blockHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
            final Sha256Hash commitmentHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
            final Long totalByteCount = byteArrayReader.readLong(8, Endian.LITTLE);
            final Long bucketCount = byteArrayReader.readVariableLengthInteger();
            if (bucketCount > UtxoCommitmentsMessage.MAX_BUCKET_COUNT) { return null; }

            final MutableList<UtxoCommitmentBucket> utxoCommitmentBuckets = new MutableList<>(bucketCount.intValue());
            for (int j = 0; j < bucketCount; ++j) {
                final Sha256Hash bucketHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
                final Long bucketByteCount = byteArrayReader.readLong(8, Endian.LITTLE);
                final Long subBucketCount = byteArrayReader.readVariableLengthInteger();

                final MutableList<MultisetBucket> subBuckets = new MutableList<>(subBucketCount.intValue());
                for (int k = 0; k < subBucketCount; ++k) {
                    final Sha256Hash subBucketHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
                    final Long subBucketByteCount = byteArrayReader.readLong(8, Endian.LITTLE);
                    if (bucketCount > UtxoCommitmentsMessage.MAX_SUB_BUCKET_COUNT) { return null; }

                    final MultisetBucket subBucket = new MultisetBucket(subBucketHash, subBucketByteCount);
                    subBuckets.add(subBucket);
                }

                final UtxoCommitmentBucket utxoCommitmentBucket = new UtxoCommitmentBucket(bucketHash, bucketByteCount, subBuckets);
                utxoCommitmentBuckets.add(utxoCommitmentBucket);
            }

            final UtxoCommitmentMetadata utxoCommitmentMetadata = new UtxoCommitmentMetadata(blockHash, commitmentHash, totalByteCount);
            utxoCommitmentsMessage.addUtxoCommitment(utxoCommitmentMetadata, utxoCommitmentBuckets);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return utxoCommitmentsMessage;
    }
}
