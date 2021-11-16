package com.softwareverde.bitcoin.server.message.header;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.MessageTypeInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessageHeader;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class BitcoinProtocolMessageHeaderInflater implements ProtocolMessageHeaderInflater {
    public static final Integer MAX_PACKET_SIZE = (int) (2L * ByteUtil.Unit.Binary.MEBIBYTES); // Except Block Messages...
    public static final Integer HEADER_BYTE_COUNT = 24;

    protected final MessageTypeInflater _messageTypeInflater;

    protected BitcoinProtocolMessageHeader _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final byte[] magicNumber = byteArrayReader.readBytes(4, Endian.LITTLE);

        { // Validate Magic Number
            if (! ByteUtil.areEqual(BitcoinProtocolMessage.BINARY_PACKET_FORMAT.getMagicNumber(), MutableByteArray.wrap(magicNumber))) {
                Logger.debug("Invalid Packet Magic Number: " + MutableByteArray.wrap(magicNumber));
                return null;
            }
        }

        final ByteArray commandBytes = MutableByteArray.wrap(byteArrayReader.readBytes(12, Endian.BIG));
        final MessageType command = _messageTypeInflater.fromBytes(commandBytes);
        // if (command == null) { return null; } // NOTE: unknown/unsupported commands are allowed but then discarded.

        final Integer payloadByteCount = byteArrayReader.readInteger(4, Endian.LITTLE);
        final byte[] payloadChecksum = byteArrayReader.readBytes(4, Endian.BIG);

        return new BitcoinProtocolMessageHeader(magicNumber, command, payloadByteCount, payloadChecksum);
    }

    public BitcoinProtocolMessageHeaderInflater() {
        _messageTypeInflater = new MessageTypeInflater();
    }

    public BitcoinProtocolMessageHeaderInflater(final MessageTypeInflater messageTypeInflater) {
        _messageTypeInflater = messageTypeInflater;
    }

    @Override
    public Integer getHeaderByteCount() {
        return HEADER_BYTE_COUNT;
    }

    @Override
    public Integer getMaxPacketByteCount(final ProtocolMessageHeader protocolMessageHeader) {
        if (protocolMessageHeader instanceof BitcoinProtocolMessageHeader) {
            final BitcoinProtocolMessageHeader bitcoinProtocolMessageHeader = (BitcoinProtocolMessageHeader) protocolMessageHeader;

            final MessageType messageType = bitcoinProtocolMessageHeader.command;
            if ( (messageType != null) && messageType.isLargeMessage() ) {
                return (2 * BitcoinConstants.getBlockMaxByteCount());
            }
        }

        return MAX_PACKET_SIZE;
    }

    @Override
    public BitcoinProtocolMessageHeader fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }

    @Override
    public BitcoinProtocolMessageHeader fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }
}
