package com.softwareverde.bitcoin.server.message.type.query.response.utxo;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class UtxoCommitmentMessageInflater extends BitcoinProtocolMessageInflater {

    public UtxoCommitmentMessageInflater() { }

    @Override
    public UtxoCommitmentMessage fromBytes(final byte[] bytes) {
        final UtxoCommitmentMessage utxoCommitmentMessage = new UtxoCommitmentMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.UTXO_COMMITMENT);
        if (protocolMessageHeader == null) { return null; }

        final PublicKey multisetPublicKey = PublicKey.fromBytes(byteArrayReader.readBytes(PublicKey.COMPRESSED_BYTE_COUNT));
        utxoCommitmentMessage.setMultisetPublicKey(multisetPublicKey);

        final CompactVariableLengthInteger byteCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! byteCount.isCanonical()) { return null; }
        if (byteCount.value > UtxoCommitmentMessage.MAX_BUCKET_BYTE_COUNT) { return null; }

        final ByteArray utxoCommitmentBytes = ByteArray.wrap(byteArrayReader.readBytes(byteCount.intValue()));
        utxoCommitmentMessage.setUtxoCommitmentBytes(utxoCommitmentBytes);

        if (byteArrayReader.didOverflow()) { return null; }

        return utxoCommitmentMessage;
    }
}
