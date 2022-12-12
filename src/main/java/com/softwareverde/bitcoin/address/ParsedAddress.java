package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Base32Util;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class ParsedAddress implements TypedAddress {
    public static String toBase58CheckEncoded(final Address address) {
        final ParsedAddress parsedAddress = new ParsedAddress(AddressType.P2PKH, false, address);
        return parsedAddress.toBase58CheckEncoded();
    }

    public static String toBase32CheckEncoded(final Address address) {
        final ParsedAddress parsedAddress = new ParsedAddress(AddressType.P2PKH, false, address);
        return parsedAddress.toBase32CheckEncoded();
    }

    protected static ByteArray calculateChecksum(final byte addressPrefix, final ByteArray bytes) {
        final MutableByteArray versionPayload = new MutableByteArray(bytes.getByteCount() + 1);
        versionPayload.setByte(0, addressPrefix);
        versionPayload.setBytes(1, bytes);

        final ByteArray fullChecksum = HashUtil.doubleSha256(versionPayload);
        return ByteArray.wrap(fullChecksum.getBytes(0, CHECKSUM_BYTE_COUNT));
    }

    public static final String BASE_32_BCH_LABEL = "bitcoincash";
    public static final String BASE_32_SLP_LABEL = "simpleledger";
    public static final String BASE_32_TESTNET_BCH_LABEL = "bchtest";

    protected static final Integer PREFIX_BYTE_COUNT = 1;
    protected static final Integer CHECKSUM_BYTE_COUNT = 4;

    protected final String _prefix;
    protected final AddressFormat _format;

    protected final Address _address;
    protected final AddressType _type;
    protected final Boolean _isTokenAware;

    protected String _toBase58CheckEncoded() {
        final ByteArray addressWithChecksum = _calculateAddressWithChecksum(_type.getBase58Prefix());
        return BitcoinUtil.toBase58String(addressWithChecksum);
    }

    protected String _toBase32CheckEncoded(final String label, final Boolean includeLabel) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        final byte prefixByte = (byte) ((_type.getBase32Prefix(_isTokenAware) << 3) & 0x78); // 0x78 = 0b 0111 1000
        final byte hashByteCountEncoding = (byte) (((_address.getByteCount() - Address.BYTE_COUNT_RIPE_MD) / 4) & 0x07);
        final byte versionByte = (byte) (prefixByte | hashByteCountEncoding);
        byteArrayBuilder.appendByte(versionByte);
        byteArrayBuilder.appendBytes(_address);

        final String encodedStringWithoutChecksum = Base32Util.toBase32String(byteArrayBuilder.build());

        final ByteArray checksumPreImage = AddressInflater.buildBase32ChecksumPreImage(label, versionByte, _address);
        final ByteArray checksum = AddressInflater.calculateBase32Checksum(checksumPreImage);
        final String checksumString = Base32Util.toBase32String(checksum.getBytes());

        final String prefix = (( includeLabel && (! Util.isBlank(label)) ) ? (label + ":") : "");
        return ( prefix + encodedStringWithoutChecksum + checksumString);
    }

    protected ByteArray _calculateAddressWithChecksum(final byte addressPrefix) {
        final ByteArray checksum = ParsedAddress.calculateChecksum(addressPrefix, _address);

        final int byteCount = (1 + _address.getByteCount() + checksum.getByteCount());
        final MutableByteArray bytes = new MutableByteArray(byteCount);

        bytes.setByte(0, addressPrefix);
        bytes.setBytes(1, _address);

        final int checksumIndex = (byteCount - checksum.getByteCount());
        bytes.setBytes(checksumIndex, checksum);

        return bytes;
    }

    public ParsedAddress(final AddressType type, final Boolean isTokenAware, final Address address) {
        this(type, isTokenAware, address, AddressFormat.BASE_58, null);
    }

    public ParsedAddress(final AddressType type, final Boolean isTokenAware, final Address address, final AddressFormat format, final String prefix) {
        _type = type;
        _isTokenAware = isTokenAware;
        _address = address;

        _prefix = prefix;
        _format = format;
    }

    public ParsedAddress(final TypedAddress typedAddress, final Boolean isTokenAware, final AddressFormat format, final String prefix) {
        _type = typedAddress.getType();
        _isTokenAware = isTokenAware;
        _address = typedAddress.getBytes();

        _prefix = prefix;
        _format = format;
    }

    public AddressFormat getFormat() {
        return _format;
    }

    public AddressType getType() { return _type; }

    public Boolean isTokenAware() { return _isTokenAware; }

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
    public Address getBytes() {
        return _address;
    }

    public String getPrefix() {
        return _prefix;
    }

    @Override
    public String toString() {
        if (_format == AddressFormat.BASE_58) {
            return _toBase58CheckEncoded();
        }
        else {
            return _toBase32CheckEncoded(_prefix, true);
        }
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof ParsedAddress)) { return false; }

        final ParsedAddress parsedAddress = (ParsedAddress) object;
        if (! Util.areEqual(_format, parsedAddress.getFormat())) { return false; }
        if (! Util.areEqual(_prefix, parsedAddress.getPrefix())) { return false; }
        if (! Util.areEqual(_type, parsedAddress.getType())) { return false; }
        if (! Util.areEqual(_isTokenAware, parsedAddress.isTokenAware())) { return false; }
        if (! Util.areEqual(_address, parsedAddress.getBytes())) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return _address.hashCode();
    }
}
