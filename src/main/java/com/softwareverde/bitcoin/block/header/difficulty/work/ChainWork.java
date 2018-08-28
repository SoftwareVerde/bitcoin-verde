package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.HexUtil;

public interface ChainWork extends Work {
    static MutableChainWork fromByteArray(final ByteArray byteArray) {
        if (byteArray.getByteCount() != 32) { return null; }
        return new MutableChainWork(byteArray);
    }

    static MutableChainWork wrap(final byte[] bytes) {
        if (bytes.length != 32) { return null; }
        return new MutableChainWork(bytes);
    }

    static MutableChainWork fromHexString(final String hexString) {
        return ChainWork.wrap(HexUtil.hexStringToByteArray(hexString));
    }

    static MutableChainWork add(final Work work0, final Work work1) {
        final MutableChainWork mutableChainWork = new MutableChainWork(work0);
        mutableChainWork.add(work1);
        return mutableChainWork;
    }
}
