package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.StringUtil;

public class ScriptBuilder {

    // NOTE: Also known as payToPublicKeyHash (or P2PKH)...
    public static ImmutableScript payToAddress(final String base58Address) {
        final byte[] addressBytes = BitcoinUtil.base58StringToBytes(base58Address);
        // TODO: Validate checksum...

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(Operation.SubType.COPY_1ST.getValue());
        byteArrayBuilder.appendByte(Operation.SubType.RIPEMD_160.getValue());
        byteArrayBuilder.appendByte((byte) addressBytes.length);
        byteArrayBuilder.appendBytes(addressBytes, Endian.BIG);
        byteArrayBuilder.appendByte(Operation.SubType.IS_EQUAL_THEN_VERIFY.getValue());
        byteArrayBuilder.appendByte(Operation.SubType.CHECK_SIGNATURE.getValue());
        return new ImmutableScript(byteArrayBuilder.build());
    }

    final ByteArrayBuilder _byteArrayBuilder = new ByteArrayBuilder();

    public ScriptBuilder pushString(final String stringData) {

        final Integer stringDataByteCount = stringData.length();

        if (stringDataByteCount == 0) {
            // Nothing.
        }
        else if (stringDataByteCount <= Operation.SubType.PUSH_DATA.getMaxValue()) {
            _byteArrayBuilder.appendByte((byte) (stringDataByteCount.intValue()));
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else if (stringDataByteCount <= 0xFFL) {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_BYTE.getValue());
            _byteArrayBuilder.appendByte((byte) (stringDataByteCount.intValue()));
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else if (stringDataByteCount <= 0xFFFFL) {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_SHORT.getValue());
            final byte[] stringDataByteCountBytes = ByteUtil.integerToBytes(stringDataByteCount);
            _byteArrayBuilder.appendBytes(new byte[] { stringDataByteCountBytes[3], stringDataByteCountBytes[4] }, Endian.LITTLE);
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_INTEGER.getValue());
            _byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(stringDataByteCount), Endian.LITTLE);
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }

        return this;
    }

    public ImmutableScript build() {
        return new ImmutableScript(_byteArrayBuilder.build());
    }
}
