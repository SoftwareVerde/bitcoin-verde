package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Base32Util;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class Address extends ImmutableByteArray {
    public enum Type {
        P2PKH, P2SH;

        public static Type fromBase58Prefix(final byte b) {
            if (b == 0x00) { return P2PKH; }
            if (b == 0x05) { return P2SH; }
            return null;
        }

        public static Type fromBase32Prefix(final byte b) {
            if (b == 0x00) { return P2PKH; }
            if (b == 0x01) { return P2SH; }
            return null;
        }

        public byte getBase58Prefix() {
            if (this == P2PKH) { return 0x00; }
            if (this == P2SH) { return 0x05; }
            throw new RuntimeException("Invalid Address Type."); // Cannot happen.
        }

        public byte getBase32Prefix() {
            if (this == P2PKH) { return 0x00; }
            if (this == P2SH) { return 0x01; }
            throw new RuntimeException("Invalid Address Type."); // Cannot happen.
        }
    }

    public static final Integer BYTE_COUNT = 20;
    public static final String BASE_32_BCH_LABEL = "bitcoincash";
    public static final String BASE_32_SLP_LABEL = "simpleledger";
    public static final String BASE_32_TESTNET_BCH_LABEL = "bchtest";

    protected static final Integer PREFIX_BYTE_COUNT = 1;
    protected static final Integer CHECKSUM_BYTE_COUNT = 4;

    protected Type _type;
    protected Boolean _isCompressed;

    protected static byte[] _calculateChecksum(final byte addressPrefix, final byte[] bytes) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(addressPrefix);
        byteArrayBuilder.appendBytes(bytes);
        final byte[] versionPayload = byteArrayBuilder.build();

        final byte[] fullChecksum = HashUtil.doubleSha256(versionPayload);
        return ByteUtil.copyBytes(fullChecksum, 0, CHECKSUM_BYTE_COUNT);
    }

    protected String _toBase58CheckEncoded() {
        final byte[] addressWithChecksum = _calculateAddressWithChecksum(_type.getBase58Prefix());
        return BitcoinUtil.toBase58String(addressWithChecksum);
    }

    protected String _toBase32CheckEncoded(final String label, final Boolean includeLabel) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        final byte prefixByte = (byte) ((_type.getBase32Prefix() << 3) & 0x78); // 0x78 = 0b 0111 1000
        final byte hashByteCountEncoding = (byte) (((_bytes.length - 20) / 4) & 0x07);
        final byte versionByte = (byte) (prefixByte | hashByteCountEncoding);
        byteArrayBuilder.appendByte(versionByte);
        byteArrayBuilder.appendBytes(_bytes);

        final String encodedStringWithoutChecksum = Base32Util.toBase32String(byteArrayBuilder.build());

        final ByteArray checksumPreImage = AddressInflater.buildBase32ChecksumPreImage(label, versionByte, this);
        final ByteArray checksum = AddressInflater.calculateBase32Checksum(checksumPreImage);
        final String checksumString = Base32Util.toBase32String(checksum.getBytes());

        return ( (includeLabel ? (label + ":") : "") + encodedStringWithoutChecksum + checksumString);
    }

    protected byte[] _calculateAddressWithChecksum(final byte addressPrefix) {
        final byte[] checksum = _calculateChecksum(addressPrefix, _bytes);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(addressPrefix);
        byteArrayBuilder.appendBytes(_bytes);
        byteArrayBuilder.appendBytes(checksum);
        return byteArrayBuilder.build();
    }

    protected Address(final Type type, final byte[] bytes, final Boolean isCompressed) {
        super(bytes);
        _type = type;
        _isCompressed = isCompressed;
    }

    public Type getType() { return _type; }

    public Boolean isCompressed() { return _isCompressed; }

    public String toBase58CheckEncoded() {
        return _toBase58CheckEncoded();
    }

    public String toBase32CheckEncoded() {
        return _toBase32CheckEncoded(BASE_32_BCH_LABEL, true);
    }

    public String toBase32CheckEncoded(final Boolean includeLabel) {
        return _toBase32CheckEncoded(BASE_32_BCH_LABEL, includeLabel);
    }

    public String toBase32CheckEncoded(final String label, final Boolean includeLabel) {
        return _toBase32CheckEncoded(label, includeLabel);
    }

    @Override
    public Address asConst() {
        return this;
    }

    @Override
    public String toString() {
        return _toBase58CheckEncoded();
    }
}
