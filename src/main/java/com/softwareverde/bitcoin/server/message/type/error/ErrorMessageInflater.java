package com.softwareverde.bitcoin.server.message.type.error;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class ErrorMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public ErrorMessage fromBytes(final byte[] bytes) {
        final ErrorMessage errorMessage = new ErrorMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.ERROR);
        if (protocolMessageHeader == null) { return null; }

        final String errorMessageString = CompactVariableLengthInteger.readVariableLengthString(byteArrayReader);
        final ErrorMessage.RejectMessageType rejectMessageType = ErrorMessage.RejectMessageType.fromString(errorMessageString);
        final byte rejectCode = byteArrayReader.readByte();
        errorMessage._rejectCode = ErrorMessage.RejectCode.fromData(rejectMessageType, rejectCode);

        errorMessage._rejectDescription = CompactVariableLengthInteger.readVariableLengthString(byteArrayReader);

        errorMessage._extraData = byteArrayReader.readBytes(byteArrayReader.remainingByteCount(), Endian.LITTLE);

        // if (byteArrayReader.didOverflow()) { return null; }

        return errorMessage;
    }
}
