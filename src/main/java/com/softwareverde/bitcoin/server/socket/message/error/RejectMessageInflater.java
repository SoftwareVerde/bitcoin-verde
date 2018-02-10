package com.softwareverde.bitcoin.server.socket.message.error;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class RejectMessageInflater extends ProtocolMessageInflater {

    @Override
    public RejectMessage fromBytes(final byte[] bytes) {
        final RejectMessage rejectMessage = new RejectMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.REJECT);
        if (protocolMessageHeader == null) { return null; }

        final RejectMessage.RejectMessageType rejectMessageType = RejectMessage.RejectMessageType.fromString(byteArrayReader.readVariableLengthString());
        final byte rejectCode = byteArrayReader.readByte();
        rejectMessage._rejectCode = RejectMessage.RejectCode.fromData(rejectMessageType, rejectCode);

        rejectMessage._rejectDescription = byteArrayReader.readVariableLengthString();

        rejectMessage._extraData = byteArrayReader.readBytes(byteArrayReader.remainingByteCount(), Endian.BIG);

        return rejectMessage;
    }
}
