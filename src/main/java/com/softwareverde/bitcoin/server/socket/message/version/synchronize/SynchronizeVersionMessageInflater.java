package com.softwareverde.bitcoin.server.socket.message.version.synchronize;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class SynchronizeVersionMessageInflater implements ProtocolMessageInflater {

    @Override
    public SynchronizeVersionMessage fromBytes(final byte[] bytes) {
        final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
        final ProtocolMessageHeaderParser protocolMessageHeaderParser = new ProtocolMessageHeaderParser();

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        final ProtocolMessageHeader protocolMessageHeader = protocolMessageHeaderParser.fromBytes(byteArrayReader);

        { // Validate Command Type
            if (ProtocolMessage.Command.SYNCHRONIZE_VERSION != protocolMessageHeader.command) {
                return null;
            }
        }

        final Integer actualPayloadByteCount = byteArrayReader.remainingByteCount();
        { // Validate Payload Byte Count
            if (protocolMessageHeader.payloadByteCount != actualPayloadByteCount) {
                System.out.println("Bad Payload size. "+ protocolMessageHeader.payloadByteCount +" != "+ actualPayloadByteCount);
                return null;
            }
        }

        final byte[] payload = byteArrayReader.peakBytes(protocolMessageHeader.payloadByteCount, Endian.BIG);

        { // Validate Checksum
            final byte[] calculatedChecksum = ProtocolMessage.calculateChecksum(payload);
            if (! ByteUtil.areEqual(protocolMessageHeader.payloadChecksum, calculatedChecksum)) {
                return null;
            }
        }

        synchronizeVersionMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Long nodeFeatureFlags = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._nodeFeatures.setFeatureFlags(nodeFeatureFlags);

        synchronizeVersionMessage._timestamp = byteArrayReader.readLong(8, Endian.LITTLE);

        final byte[] remoteNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._remoteNetworkAddress = NetworkAddress.fromBytes(remoteNetworkAddressBytes);

        final byte[] localNetworkAddressBytes = byteArrayReader.readBytes(26, Endian.BIG);
        synchronizeVersionMessage._localNetworkAddress = NetworkAddress.fromBytes(localNetworkAddressBytes);

        synchronizeVersionMessage._nonce = byteArrayReader.readLong(8, Endian.LITTLE);
        synchronizeVersionMessage._userAgent = byteArrayReader.readVariableLengthString();
        synchronizeVersionMessage._currentBlockHeight = byteArrayReader.readInteger(4, Endian.LITTLE);
        synchronizeVersionMessage._relayIsEnabled = byteArrayReader.readBoolean();
        return synchronizeVersionMessage;
    }

}
