package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class UtxoCommitmentMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_BUCKET_BYTE_COUNT = (int) (32L * ByteUtil.Unit.Binary.MEBIBYTES);

    protected Sha256Hash _multisetHash;
    protected ByteArray _utxoCommitmentBytes;

    public UtxoCommitmentMessage() {
        super(MessageType.UTXO_COMMITMENT);
    }

    public Sha256Hash getMultisetHash() {
        return _multisetHash;
    }

    public void setMultisetHash(final Sha256Hash multisetHash) {
        _multisetHash = multisetHash;
    }

    public ByteArray getUtxoCommitmentBytes() {
        return _utxoCommitmentBytes;
    }

    public void setUtxoCommitmentBytes(final ByteArray byteArray) {
        _utxoCommitmentBytes = byteArray;
    }

    @Override
    protected ByteArray _getPayload() {
        final int byteCount = _utxoCommitmentBytes.getByteCount();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(_multisetHash, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(byteCount));
        byteArrayBuilder.appendBytes(_utxoCommitmentBytes);

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final int byteCount = _utxoCommitmentBytes.getByteCount();
        final byte[] byteCountByteCount = ByteUtil.variableLengthIntegerToBytes(byteCount);

        return (Sha256Hash.BYTE_COUNT + byteCountByteCount.length + _utxoCommitmentBytes.getByteCount());
    }
}
