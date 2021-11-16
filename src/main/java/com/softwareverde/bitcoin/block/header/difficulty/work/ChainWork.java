package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;

import java.math.BigInteger;
import java.util.Comparator;

public interface ChainWork extends Work, Comparable<ChainWork> {
    Integer BYTE_COUNT = Sha256Hash.BYTE_COUNT;

    Comparator<ChainWork> COMPARATOR = new Comparator<ChainWork>() {
        @Override
        public int compare(final ChainWork chainWork0, final ChainWork chainWork1) {
            for (int i = 0; i < ChainWork.BYTE_COUNT; ++i) {
                final byte b0 = chainWork0.getByte(i);
                final byte b1 = chainWork1.getByte(i);

                final int byteCompare = ByteUtil.compare(b0, b1);
                if (byteCompare == 0) { continue; }

                return byteCompare;
            }

            return 0;
        }
    };

    static MutableChainWork copyOf(final ByteArray byteArray) {
        if (byteArray.getByteCount() != 32) { return null; }
        return new MutableChainWork(byteArray);
    }

    static MutableChainWork fromBigInteger(final BigInteger bigInteger) {
        final byte[] bytes = bigInteger.toByteArray();
        final MutableChainWork mutableChainWork = new MutableChainWork();
        for (int i = 0; i < bytes.length; ++i) {
            mutableChainWork.setByte((32 - i -1), (bytes[bytes.length - i - 1]));
        }
        return mutableChainWork;
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

    @Override
    default int compareTo(final ChainWork chainWork) {
        return ChainWork.COMPARATOR.compare(ChainWork.this, chainWork);
    }
}
