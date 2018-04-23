package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class MutableHash extends MutableByteArray implements Hash {
    public static MutableHash fromHexString(final String hexString) {
        final byte[] hashBytes = HexUtil.hexStringToByteArray(hexString);
        return new MutableHash(hashBytes);
    }

    public static MutableHash wrap(final byte[] bytes) {
        if (bytes.length != SHA_256_BYTE_COUNT && bytes.length != RIPEMD_160_BYTE_COUNT) {
            Logger.log("NOTICE: Unable to wrap bytes as hash. Invalid byte count: "+ bytes.length);
            return null;
        }
        return new MutableHash(bytes);
    }

    public static MutableHash copyOf(final byte[] bytes) {
        if (bytes.length != SHA_256_BYTE_COUNT && bytes.length != RIPEMD_160_BYTE_COUNT) {
            Logger.log("NOTICE: Unable to wrap bytes as hash. Invalid byte count: "+ bytes.length);
            return null;
        }
        return new MutableHash(ByteUtil.copyBytes(bytes));
    }

    protected MutableHash(final byte[] bytes) {
        super(bytes);
    }

    public MutableHash() {
        super(SHA_256_BYTE_COUNT);
    }

    public MutableHash(final Hash hash) {
        super(hash.getByteCount());
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    @Override
    public Hash toReversedEndian() {
        return new MutableHash(ByteUtil.reverseEndian(_bytes));
    }

    public void setBytes(final byte[] bytes) {
        if (bytes.length != SHA_256_BYTE_COUNT && bytes.length != RIPEMD_160_BYTE_COUNT) {
            Logger.log("NOTICE: Attempted to set hash bytes of incorrect length: "+ bytes.length);
            return;
        }

        if (_bytes.length != bytes.length) {
            _bytes = new byte[bytes.length];
        }
        ByteUtil.setBytes(_bytes, bytes);
    }

    public void setBytes(final Hash hash) {
        if (_bytes.length != hash.getByteCount()) {
            _bytes = new byte[hash.getByteCount()];
        }
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    @Override
    public ImmutableHash asConst() {
        return new ImmutableHash(this);
    }
}
