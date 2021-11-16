package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class QueryUtxoCommitmentsMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public QueryUtxoCommitmentsMessage fromBytes(final byte[] bytes) {
        final QueryUtxoCommitmentsMessage queryUtxoCommitmentsMessage = new QueryUtxoCommitmentsMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_UTXO_COMMITMENTS);
        if (protocolMessageHeader == null) { return null; }
        if (byteArrayReader.didOverflow()) { return null; }

        return queryUtxoCommitmentsMessage;
    }
}
