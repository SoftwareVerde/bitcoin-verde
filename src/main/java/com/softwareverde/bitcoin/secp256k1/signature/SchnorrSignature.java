package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class SchnorrSignature implements Signature {
    public static final Integer BYTE_COUNT = 64;

    public static SchnorrSignature fromBytes(final ByteArray byteArray) {
        if (byteArray.getByteCount() != BYTE_COUNT) { return null; }

        final MutableByteArray rBytes = MutableByteArray.wrap(byteArray.getBytes(0, 32));
        final MutableByteArray sBytes = MutableByteArray.wrap(byteArray.getBytes(32, 32));
        return new SchnorrSignature(rBytes, sBytes);
    }

    protected final ByteArray _r;
    protected final ByteArray _s;

    private SchnorrSignature(final MutableByteArray r, final MutableByteArray s) {
        _r = r;
        _s = s;
    }

    public SchnorrSignature(final byte[] r, final byte[] s) {
        _r = new ImmutableByteArray(r);
        _s = new ImmutableByteArray(s);
    }

    public SchnorrSignature(final ByteArray r, final ByteArray s) {
        _r = r.asConst();
        _s = s.asConst();
    }

    @Override
    public Type getType() {
        return Type.SCHNORR;
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
        final MutableByteArray byteArray = new MutableByteArray(BYTE_COUNT);
        ByteUtil.setBytes(byteArray, _r, 0);
        ByteUtil.setBytes(byteArray, _s, 32);
        return byteArray;
    }

    @Override
    public Boolean isCanonical() {
        return true;
    }

    @Override
    public Signature asCanonical() {
        return this;
    }
}
