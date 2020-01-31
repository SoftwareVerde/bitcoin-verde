package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Base64Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class BitcoinMessageSignature extends SignatureCore {
    public static BitcoinMessageSignature decode(final ByteArray bytes) {
        if (bytes.getByteCount() != 65) { return null; }

        final byte headerByte = bytes.getByte(0);

        final int headerInteger = (ByteUtil.byteToInteger(headerByte));
        if ( (headerInteger < 27) || (headerInteger > 34) ) { return null; }

        final ByteArray r = MutableByteArray.wrap(bytes.getBytes(1, 32));
        final ByteArray s = MutableByteArray.wrap(bytes.getBytes(33, 32));
        return new BitcoinMessageSignature(headerInteger, r, s);
    }

    public static BitcoinMessageSignature fromBase64(final String base64) {
        final ByteArray bytes = MutableByteArray.wrap(Base64Util.base64StringToByteArray(base64));
        return BitcoinMessageSignature.decode(bytes);
    }

    public static BitcoinMessageSignature fromSignature(final Signature signature, final Integer recoveryId, final Boolean isCompressed) {
        final int headerInteger = (recoveryId + 27 + (isCompressed ? 4 : 0));
        final ByteArray r = MutableByteArray.wrap(ByteUtil.getTailBytes(signature.getR(), 32));
        final ByteArray s = MutableByteArray.wrap(ByteUtil.getTailBytes(signature.getS(), 32));
        return new BitcoinMessageSignature(headerInteger, r, s);
    }

    protected final Integer _headerByte;
    protected final ByteArray _r;
    protected final ByteArray _s;

    protected BitcoinMessageSignature(final Integer headerByte, final ByteArray r, final ByteArray s) {
        _headerByte = headerByte;
        _r = ConstUtil.asConstOrNull(r);
        _s = ConstUtil.asConstOrNull(s);
    }

    protected ByteArray _encode() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(_headerByte.byteValue());
        byteArrayBuilder.appendBytes(_r);
        byteArrayBuilder.appendBytes(_s);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    @Override
    public Type getType() {
        return Type.BITCOIN_MESSAGE;
    }

    @Override
    public ByteArray getR() {
        return _r;
    }

    @Override
    public ByteArray getS() {
        return _s;
    }

    @Override
    public ByteArray encode() {
        return _encode();
    }

    @Override
    public Boolean isCanonical() {
        return true;
    }

    @Override
    public BitcoinMessageSignature asCanonical() {
        return this;
    }

    @Override
    public Boolean isEmpty() {
        return false;
    }

    public Boolean isCompressedAddress() {
        return (_headerByte >= 31);
    }

    public Integer getRecoveryId() {
        return (_headerByte - (27 + (_headerByte >= 31 ? 4 : 0)));
    }

    public String toBase64() {
        final ByteArray encodedByteArray = _encode();
        return Base64Util.toBase64String(encodedByteArray.getBytes());
    }
}
