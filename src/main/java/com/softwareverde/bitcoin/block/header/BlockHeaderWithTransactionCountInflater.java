package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class BlockHeaderWithTransactionCountInflater extends BlockHeaderInflater {
    @Override
    protected MutableBlockHeaderWithTransactionCount _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableBlockHeader blockHeader = super._fromByteArrayReader(byteArrayReader);
        if (blockHeader == null) { return null; }

        final CompactVariableLengthInteger transactionCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! transactionCount.isCanonical()) { return null; }

        return new MutableBlockHeaderWithTransactionCount(blockHeader, transactionCount.intValue());
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
