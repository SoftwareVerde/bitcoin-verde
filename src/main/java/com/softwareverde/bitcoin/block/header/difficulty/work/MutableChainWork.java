package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;

public class MutableChainWork extends MutableByteArray implements ChainWork {
    /**
     * NOTICE: This function does not copy the provided byte array.
     */
    protected MutableChainWork(final byte[] bytes) {
        super(bytes);
    }

    protected MutableChainWork(final ByteArray byteArray) {
        super(byteArray);
    }

    public MutableChainWork() {
        super(32);
    }

    public MutableChainWork(final BlockWork blockWork) {
        super(blockWork);
    }

    public void add(final Work blockWork) {
        int overflow = 0;
        for (int i = 31; i >= 0; i--) {
            final int value = (ByteUtil.byteToInteger(_bytes[i]) + ByteUtil.byteToInteger(blockWork.getByte(i)) + overflow);
            _bytes[i] = (byte) value;
            overflow = value >>> 8;
        }

        if (overflow != 0) {
            Logger.log("NOTICE: ChainWork overflow!");
            for (int i = 0; i < _bytes.length; ++i) {
                _bytes[i] = (byte) 0xFF;
            }
        }
    }
}
