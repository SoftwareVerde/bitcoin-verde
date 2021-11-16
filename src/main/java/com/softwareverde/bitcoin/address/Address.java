package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Base32Util;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class Address extends ImmutableByteArray {
    public static final Integer BYTE_COUNT = 20;
    public static final byte PREFIX = 0x00;
    public static final byte BASE_32_PREFIX = 0x00;
    public static final String BASE_32_BCH_LABEL = "bitcoincash";
    public static final String BASE_32_SLP_LABEL = "simpleledger";
    public static final String BASE_32_TESTNET_BCH_LABEL = "bchtest";

    protected static final Integer PREFIX_BYTE_COUNT = 1;
    protected static final Integer CHECKSUM_BYTE_COUNT = 4;

    protected static byte[] _calculateChecksum(final byte addressPrefix, final byte[] bytes) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(addressPrefix);
        byteArrayBuilder.appendBytes(bytes);
        final byte[] versionPayload = byteArrayBuilder.build();

        final byte[] fullChecksum = HashUtil.doubleSha256(versionPayload);
        return ByteUtil.copyBytes(fullChecksum, 0, CHECKSUM_BYTE_COUNT);
    }

    protected String _toBase32CheckEncoded(final String label, final Boolean includeLabel) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        final byte prefixByte = (byte) (_getBase32Prefix() & 0x78);
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

    protected byte _getPrefix() {
        return PREFIX;
    }

    protected byte _getBase32Prefix() { return BASE_32_PREFIX; }

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
        final byte[] addressWithChecksum = _calculateAddressWithChecksum();
        return BitcoinUtil.toBase58String(addressWithChecksum);
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
        final byte[] addressWithChecksum = _calculateAddressWithChecksum();
        return BitcoinUtil.toBase58String(addressWithChecksum);
    }
}
