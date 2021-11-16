package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;

public class DoubleSpendProofMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public DoubleSpendProofMessage fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.DOUBLE_SPEND_PROOF);
        if (protocolMessageHeader == null) { return null; }

        final DoubleSpendProofInflater doubleSpendProofInflater = new DoubleSpendProofInflater();
        final DoubleSpendProof doubleSpendProof = doubleSpendProofInflater.fromBytes(byteArrayReader);
        if (doubleSpendProof == null) { return null; }
        if (byteArrayReader.didOverflow()) { return null; }

        return new DoubleSpendProofMessage(doubleSpendProof);
    }

    public DoubleSpendProofMessage fromBytes(final ByteArray byteArray) {
        return this.fromBytes(byteArray.getBytes());
    }
}
