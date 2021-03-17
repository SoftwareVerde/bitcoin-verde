package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class DoubleSpendProofMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public DoubleSpendProofMessage fromBytes(final byte[] bytes) {
        final DoubleSpendProofMessage doubleSpendProofMessage = new DoubleSpendProofMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.DOUBLE_SPEND_PROOF);
        if (protocolMessageHeader == null) { return null; }

        final Sha256Hash previousOutputTransactionHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
        final Integer previousOutputIndex = byteArrayReader.readInteger(4, Endian.LITTLE);
        doubleSpendProofMessage._previousTransactionOutputIdentifier = new TransactionOutputIdentifier(previousOutputTransactionHash, previousOutputIndex);

        final DoubleSpendProofPreimageInflater doubleSpendProofPreimageInflater = new DoubleSpendProofPreimageInflater();
        doubleSpendProofMessage._doubleSpendProofPreimage0 = doubleSpendProofPreimageInflater.fromBytes(byteArrayReader);
        doubleSpendProofMessage._doubleSpendProofPreimage1 = doubleSpendProofPreimageInflater.fromBytes(byteArrayReader);

        if (byteArrayReader.didOverflow()) { return null; }

        return doubleSpendProofMessage;
    }
}
