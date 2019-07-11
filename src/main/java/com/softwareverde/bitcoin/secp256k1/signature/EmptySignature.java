package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class EmptySignature implements Signature {
    public static final EmptySignature SCHNORR = new EmptySignature(Type.SCHNORR);
    public static final EmptySignature SECP256K1 = new EmptySignature(Type.SECP256K1);

    protected final Type _type;
    protected final ByteArray _r = new MutableByteArray(0);
    protected final ByteArray _s = new MutableByteArray(0);

    protected EmptySignature(final Type type) {
        _type = type;
    }

    @Override
    public Type getType() {
        return _type;
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
        return new MutableByteArray(0);
    }

    @Override
    public Boolean isCanonical() {
        return true;
    }

    @Override
    public Signature asCanonical() {
        return this;
    }

    @Override
    public Boolean isEmpty() {
        return true;
    }
}
