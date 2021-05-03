package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;

public class BlockHeaderWithTransactionCountInflater extends BlockHeaderInflater {
    @Override
    protected MutableBlockHeaderWithTransactionCount _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableBlockHeader blockHeader = super._fromByteArrayReader(byteArrayReader);
        final Integer transactionCount = byteArrayReader.readVariableLengthInteger().intValue();
        return new MutableBlockHeaderWithTransactionCount(blockHeader, transactionCount);
    }

    @Override
    public MutableBlockHeaderWithTransactionCount fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    @Override
    public MutableBlockHeaderWithTransactionCount fromBytes(final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray.getBytes());
        return _fromByteArrayReader(byteArrayReader);
    }

    @Override
    public MutableBlockHeaderWithTransactionCount fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}
