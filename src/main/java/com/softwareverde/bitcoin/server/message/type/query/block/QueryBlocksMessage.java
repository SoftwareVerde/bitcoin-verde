package com.softwareverde.bitcoin.server.message.type.query.block;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class QueryBlocksMessage extends BitcoinProtocolMessage {
    public static Integer MAX_BLOCK_HASH_COUNT = 500;

    protected Integer _version;
    protected final MutableList<Sha256Hash> _blockHeaderHashes = new MutableList<Sha256Hash>();
    protected Sha256Hash _stopBeforeBlockHash = Sha256Hash.EMPTY_HASH;

    public QueryBlocksMessage() {
        super(MessageType.QUERY_BLOCKS);
        _version = Constants.PROTOCOL_VERSION;
    }

    public Integer getVersion() { return _version; }

    /**
     * NOTE: Block Hashes should be added in descending order by block height (i.e. head Block first)...
     */
    public void addBlockHash(final Sha256Hash blockHeaderHash) {
        if (_blockHeaderHashes.getSize() >= MAX_BLOCK_HASH_COUNT) { return; }
        if (blockHeaderHash == null) { return; }

        _blockHeaderHashes.add(blockHeaderHash.asConst());
    }

    public void clearBlockHeaderHashes() {
        _blockHeaderHashes.clear();
    }

    /**
     * NOTE: Block Hashes are sorted in descending order by block height (i.e. head Block first)...
     */
    public List<Sha256Hash> getBlockHashes() {
        return _blockHeaderHashes;
    }

    public Sha256Hash getStopBeforeBlockHash() {
        return _stopBeforeBlockHash;
    }

    public void setStopBeforeBlockHash(final Sha256Hash blockHeaderHash) {
        _stopBeforeBlockHash = (blockHeaderHash != null ? blockHeaderHash.asConst() : Sha256Hash.EMPTY_HASH);
    }

    @Override
    protected ByteArray _getPayload() {
        final int blockHeaderCount = _blockHeaderHashes.getSize();
        final int blockHeaderHashByteCount = 32;

        final byte[] versionBytes = ByteUtil.integerToBytes(_version);
        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);
        final byte[] blockHeaderHashesBytes = new byte[blockHeaderHashByteCount * blockHeaderCount];

        for (int i=0; i<blockHeaderCount; ++i) {
            final Sha256Hash blockHeaderHash = _blockHeaderHashes.get(_blockHeaderHashes.getSize() - i - 1);
            final int startIndex = (blockHeaderHashByteCount * i);
            ByteUtil.setBytes(blockHeaderHashesBytes, blockHeaderHash.getBytes(), startIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(versionBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(blockHeaderCountBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(blockHeaderHashesBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_stopBeforeBlockHash.getBytes(), Endian.LITTLE);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
