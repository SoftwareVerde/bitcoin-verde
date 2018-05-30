package com.softwareverde.bitcoin.type.hash.sha256;

import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class MutableSha256Hash extends MutableHash implements Sha256Hash {
    public static MutableSha256Hash fromHexString(final String hexString) {
        final byte[] hashBytes = HexUtil.hexStringToByteArray(hexString);
        if (hashBytes == null) {
            Logger.log("NOTICE: Unable to parse hash from string. Invalid hex string: "+ hexString);
            return null;
        }

        return new MutableSha256Hash(hashBytes);
    }

    public static MutableSha256Hash wrap(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) {
            Logger.log("NOTICE: Unable to wrap bytes as hash. Invalid byte count: "+ bytes.length);
            return null;
        }
        return new MutableSha256Hash(bytes);
    }

    public static MutableSha256Hash copyOf(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) {
            Logger.log("NOTICE: Unable to wrap bytes as hash. Invalid byte count: "+ bytes.length);
            return null;
        }
        return new MutableSha256Hash(ByteUtil.copyBytes(bytes));
    }

    protected MutableSha256Hash(final byte[] bytes) {
        super(bytes);
    }

    public MutableSha256Hash() {
        super(BYTE_COUNT);
    }

    public MutableSha256Hash(final Sha256Hash hash) {
        super(hash);
    }

    @Override
    public Sha256Hash toReversedEndian() {
        return MutableSha256Hash.wrap(ByteUtil.reverseEndian(_bytes));
    }

    public void setBytes(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) {
            Logger.log("NOTICE: Attempted to set hash bytes of incorrect length: "+ bytes.length);
            return;
        }

        if (_bytes.length != bytes.length) {
            _bytes = new byte[bytes.length];
        }
        ByteUtil.setBytes(_bytes, bytes);
    }

    public void setBytes(final Sha256Hash hash) {
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    @Override
    public ImmutableSha256Hash asConst() {
        return new ImmutableSha256Hash(this);
    }
}
