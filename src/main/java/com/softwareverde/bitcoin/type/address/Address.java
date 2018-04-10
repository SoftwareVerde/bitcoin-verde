package com.softwareverde.bitcoin.type.address;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.util.ByteUtil;

public class Address extends ImmutableByteArray {
    public static final byte PREFIX = 0x00;

    protected static final Integer PREFIX_BYTE_COUNT = 1;
    protected static final Integer CHECKSUM_BYTE_COUNT = 4;

    protected static byte[] _calculateChecksum(final byte addressPrefix, final byte[] bytes) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(addressPrefix);
        byteArrayBuilder.appendBytes(bytes);
        final byte[] versionPayload = byteArrayBuilder.build();

        final byte[] fullChecksum = BitcoinUtil.sha256(BitcoinUtil.sha256(versionPayload));
        return ByteUtil.copyBytes(fullChecksum, 0, CHECKSUM_BYTE_COUNT);
    }

    protected byte _getPrefix() {
        return PREFIX;
    }

    protected byte[] _calculateAddressWithChecksum() {
        final byte addressPrefix = _getPrefix();

        final byte[] checksum = _calculateChecksum(addressPrefix, _bytes);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(addressPrefix);
        byteArrayBuilder.appendBytes(_bytes);
        byteArrayBuilder.appendBytes(checksum);
        return byteArrayBuilder.build();
    }

    protected Address(final byte[] bytes) {
        super(bytes);
    }

    public Boolean isCompressed() { return false; }

    public byte[] getBytesWithChecksum() {
        return _calculateAddressWithChecksum();
    }

    public String toBase58CheckEncoded() {
        return BitcoinUtil.toBase58String(_calculateAddressWithChecksum());
    }

    @Override
    public Address asConst() {
        return this;
    }

    @Override
    public String toString() {
        return BitcoinUtil.toBase58String(_bytes);
    }
}
