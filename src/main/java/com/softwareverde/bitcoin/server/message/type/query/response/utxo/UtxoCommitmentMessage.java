package com.softwareverde.bitcoin.server.message.type.query.response.utxo;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class UtxoCommitmentMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_BUCKET_BYTE_COUNT = (int) (32L * ByteUtil.Unit.Binary.MEBIBYTES);

    protected PublicKey _multisetPublicKey;
    protected ByteArray _utxoCommitmentBytes;

    public UtxoCommitmentMessage() {
        super(MessageType.UTXO_COMMITMENT);
    }

    public PublicKey getMultisetPublicKey() {
        return _multisetPublicKey;
    }

    public void setMultisetPublicKey(final PublicKey multisetPublicKey) {
        _multisetPublicKey = multisetPublicKey.compress();
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
        byteArrayBuilder.appendBytes(_multisetPublicKey, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.longToBytes(byteCount));
        byteArrayBuilder.appendBytes(_utxoCommitmentBytes);

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final int byteCount = _utxoCommitmentBytes.getByteCount();
        final ByteArray byteCountByteCount = CompactVariableLengthInteger.variableLengthIntegerToBytes(byteCount);

        return (Sha256Hash.BYTE_COUNT + byteCountByteCount.getByteCount() + _utxoCommitmentBytes.getByteCount());
    }
}
