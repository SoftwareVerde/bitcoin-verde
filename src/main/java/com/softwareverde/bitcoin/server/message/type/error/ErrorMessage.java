package com.softwareverde.bitcoin.server.message.type.error;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class ErrorMessage extends BitcoinProtocolMessage {
    public static Integer MAX_BLOCK_HEADER_HASH_COUNT = 2000;

    public enum RejectMessageType {
        NULL(""),
        TRANSACTION("tx"),
        BLOCK("block"),
        VERSION("version");

        public static RejectMessageType fromString(final String string) {
            for (final RejectMessageType rejectMessageType : RejectMessageType.values()) {
                if (rejectMessageType._value.equals(string)) {
                    return rejectMessageType;
                }
            }

            return NULL;
        }

        private final String _value;
        RejectMessageType(final String value) {
            _value = value;
        }

        public String getValue() {
            return _value;
        }
    }

    public enum RejectCode {
        REJECT                          (RejectMessageType.NULL,        0x01),
        INVALID_BLOCK                   (RejectMessageType.BLOCK,       0x10),
        INVALID_TRANSACTION             (RejectMessageType.TRANSACTION, 0x10),
        INVALID_BLOCK_VERSION           (RejectMessageType.BLOCK,       0x11),
        UNSUPPORTED_PROTOCOL_VERSION    (RejectMessageType.VERSION,     0x11),
        DOUBLE_SPEND_DETECTED           (RejectMessageType.TRANSACTION, 0x12),
        DUPLICATE_VERSION_MESSAGE       (RejectMessageType.VERSION,     0x12),
        UNSUPPORTED_TRANSACTION_TYPE    (RejectMessageType.TRANSACTION, 0x40),
        TRANSACTION_TOO_SMALL           (RejectMessageType.TRANSACTION, 0x41),
        INSUFFICIENT_TRANSACTION_FEE    (RejectMessageType.TRANSACTION, 0x42),
        FORKED_BLOCK                    (RejectMessageType.BLOCK,       0x43);

        public static RejectCode fromData(final RejectMessageType rejectMessageType, final int code) {
            final byte rejectCodeByte = (byte) code;
            for (final RejectCode rejectCode : RejectCode.values()) {
                if ((rejectCode._rejectMessageType == rejectMessageType) && (rejectCode._code == rejectCodeByte)) {
                    return rejectCode;
                }
            }
            return REJECT;
        }

        private final byte _code;
        private final RejectMessageType _rejectMessageType;

        RejectCode(final RejectMessageType rejectMessageType, final int code) {
            _rejectMessageType = rejectMessageType;
            _code = (byte) code;
        }

        public RejectMessageType getRejectMessageType() {
            return _rejectMessageType;
        }

        public byte getCode() {
            return _code;
        }
    }

    protected RejectCode _rejectCode;
    protected String _rejectDescription;
    protected byte[] _extraData;

    public ErrorMessage() {
        super(MessageType.ERROR);
        _rejectDescription = "";
        _rejectCode = RejectCode.REJECT;
        _extraData = new byte[0];
    }

    public RejectCode getRejectCode() {
        return _rejectCode;
    }

    public String getRejectDescription() {
        return _rejectDescription;
    }

    public byte[] getExtraData() {
        return _extraData;
    }

    @Override
    protected ByteArray _getPayload() {
        final RejectMessageType rejectMessageType = _rejectCode.getRejectMessageType();
        final byte[] rejectedMessageTypeBytes = ByteUtil.variableLengthStringToBytes(rejectMessageType.getValue());
        final byte[] rejectMessageCodeBytes = new byte[]{ _rejectCode.getCode() };
        final byte[] rejectDescriptionBytes = ByteUtil.variableLengthStringToBytes(_rejectDescription);
        final byte[] extraDataBytes = _extraData;

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(rejectedMessageTypeBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(rejectMessageCodeBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(rejectDescriptionBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(extraDataBytes, Endian.BIG); // TODO: Unsure if should be Big or Little endian...
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
