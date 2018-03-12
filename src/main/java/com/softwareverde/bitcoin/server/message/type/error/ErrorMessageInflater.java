package com.softwareverde.bitcoin.server.message.type.error;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class ErrorMessageInflater extends ProtocolMessageInflater {

    @Override
    public ErrorMessage fromBytes(final byte[] bytes) {
        final ErrorMessage errorMessage = new ErrorMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.ERROR);
        if (protocolMessageHeader == null) { return null; }

        final ErrorMessage.RejectMessageType rejectMessageType = ErrorMessage.RejectMessageType.fromString(byteArrayReader.readVariableLengthString());
        final byte rejectCode = byteArrayReader.readByte();
        errorMessage._rejectCode = ErrorMessage.RejectCode.fromData(rejectMessageType, rejectCode);

        errorMessage._rejectDescription = byteArrayReader.readVariableLengthString();

        errorMessage._extraData = byteArrayReader.readBytes(byteArrayReader.remainingByteCount(), Endian.BIG);

        // if (byteArrayReader.didOverflow()) { return null; }

        return errorMessage;
    }
}
