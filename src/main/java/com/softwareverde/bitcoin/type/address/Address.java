package com.softwareverde.bitcoin.type.address;

import com.softwareverde.bitcoin.type.bytearray.ImmutableByteArray;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class Address extends ImmutableByteArray {
    public static final Byte PREFIX = 0x00;
    protected static final Integer prefixByteCount = 1;
    protected static final Integer checksumByteCount = 4;

    protected static byte[] _calculateChecksum(final byte addressPrefix, final byte[] bytes) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(addressPrefix);
        byteArrayBuilder.appendBytes(bytes);
        final byte[] versionPayload = byteArrayBuilder.build();

        final byte[] fullChecksum = BitcoinUtil.sha256(BitcoinUtil.sha256(versionPayload));
        return ByteUtil.copyBytes(fullChecksum, 0, checksumByteCount);
    }

    public static Address fromPrivateKey(final PrivateKey privateKey) {
        final PublicKey publicKey = privateKey.getPublicKey();
        final byte[] rawBitcoinAddress = BitcoinUtil.ripemd160(BitcoinUtil.sha256(publicKey.getBytes()));
        return new Address(rawBitcoinAddress);
    }

    public static Address fromPublicKey(final PublicKey publicKey) {
        final byte[] rawBitcoinAddress = BitcoinUtil.ripemd160(BitcoinUtil.sha256(publicKey.getBytes()));
        return new Address(rawBitcoinAddress);
    }

    public static Address fromBase58Check(final String base58CheckString) {
        final byte[] bytesWithPrefixWithChecksum = BitcoinUtil.base58StringToBytes(base58CheckString);
        final byte[] bytesWithoutPrefixWithoutChecksum = ByteUtil.copyBytes(bytesWithPrefixWithChecksum, prefixByteCount, bytesWithPrefixWithChecksum.length - checksumByteCount - prefixByteCount);

        final byte prefix = bytesWithPrefixWithChecksum[0];
        final byte[] checksum = ByteUtil.copyBytes(bytesWithPrefixWithChecksum, bytesWithPrefixWithChecksum.length - checksumByteCount, checksumByteCount);

        final byte[] calculatedChecksum = _calculateChecksum(prefix, bytesWithoutPrefixWithoutChecksum);

        final Boolean checksumIsValid = (ByteUtil.areEqual(calculatedChecksum, checksum));
        if (! checksumIsValid) { return null; }

        return new Address(bytesWithoutPrefixWithoutChecksum);
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
