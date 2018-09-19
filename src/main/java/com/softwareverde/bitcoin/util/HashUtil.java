package com.softwareverde.bitcoin.util;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.murmur.MurmurHashUtil;

public class HashUtil extends com.softwareverde.util.HashUtil {
    public Long murmurHash(final Long tweak, final Integer hashCount, final ByteArray bytes) {
        return MurmurHashUtil.hashVersion3x86_32(tweak, hashCount, bytes);
    }
}
