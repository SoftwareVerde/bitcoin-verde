package com.softwareverde.bitcoin.server.message.type.slp;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class EnableSlpTransactionsMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public BitcoinProtocolMessage fromBytes(final byte[] bytes) {
        final EnableSlpTransactionsMessage enableSlpTransactionsMessage = new EnableSlpTransactionsMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.ENABLE_SLP_TRANSACTIONS);
        if (protocolMessageHeader == null) { return null; }

        final Boolean isEnabled = (byteArrayReader.readInteger(4, Endian.LITTLE) > 0);

        enableSlpTransactionsMessage.setIsEnabled(isEnabled);

        if (byteArrayReader.didOverflow()) { return null; }

        return enableSlpTransactionsMessage;
    }
}
