package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.Util;

public interface BlockHeader extends Hashable, Constable<ImmutableBlockHeader>, Jsonable {
    Long VERSION = BitcoinConstants.getBlockVersion();
    Sha256Hash GENESIS_BLOCK_HASH = Sha256Hash.fromHexString(BitcoinConstants.getGenesisBlockHash());

    static Long calculateBlockReward(final Long blockHeight) {
        return ((50 * Transaction.SATOSHIS_PER_BITCOIN) >> (blockHeight / 210000));
    }

    Sha256Hash getPreviousBlockHash();
    MerkleRoot getMerkleRoot();
    Difficulty getDifficulty();
    Long getVersion();
    Long getTimestamp();
    Long getNonce();

    Boolean isValid();

    @Override
    ImmutableBlockHeader asConst();
}

abstract class BlockHeaderCore implements BlockHeader {
    protected static final BlockHasher DEFAULT_BLOCK_HASHER = new BlockHasher();

    protected final BlockHasher _blockHasher;
    protected final BlockHeaderDeflater _blockHeaderDeflater;

    protected Long _version;
    protected Sha256Hash _previousBlockHash = Sha256Hash.EMPTY_HASH;
    protected MerkleRoot _merkleRoot = null;
    protected Long _timestamp;
    protected Difficulty _difficulty;
    protected Long _nonce;

    protected BlockHeaderCore(final BlockHasher blockHasher) {
        _blockHeaderDeflater = blockHasher.getBlockHeaderDeflater();
        _blockHasher = blockHasher;
        _version = VERSION;
    }

    public BlockHeaderCore() {
        _blockHasher = DEFAULT_BLOCK_HASHER;
        _blockHeaderDeflater = _blockHasher.getBlockHeaderDeflater();
        _version = VERSION;
    }

    public BlockHeaderCore(final BlockHeader blockHeader) {
        _blockHasher = DEFAULT_BLOCK_HASHER;
        _blockHeaderDeflater = _blockHasher.getBlockHeaderDeflater();

        _version = blockHeader.getVersion();
        _previousBlockHash = (Sha256Hash) ConstUtil.asConstOrNull(blockHeader.getPreviousBlockHash());
        _merkleRoot = (MerkleRoot) ConstUtil.asConstOrNull(blockHeader.getMerkleRoot());
        _timestamp = blockHeader.getTimestamp();
        _difficulty = ConstUtil.asConstOrNull(blockHeader.getDifficulty());
        _nonce = blockHeader.getNonce();
    }

    @Override
    public Long getVersion() { return _version; }

    @Override
    public Sha256Hash getPreviousBlockHash() { return _previousBlockHash; }

    @Override
    public MerkleRoot getMerkleRoot() { return _merkleRoot; }

    @Override
    public Long getTimestamp() { return _timestamp; }

    @Override
    public Difficulty getDifficulty() { return _difficulty; }

    @Override
    public Long getNonce() { return  _nonce; }

    @Override
    public Sha256Hash getHash() {
        return _blockHasher.calculateBlockHash(this);
    }

    @Override
    public Boolean isValid() {
        final Sha256Hash calculatedHash = _blockHasher.calculateBlockHash(this);
        return (_difficulty.isSatisfiedBy(calculatedHash));
    }

    @Override
    public ImmutableBlockHeader asConst() {
        return new ImmutableBlockHeader(this);
    }

    @Override
    public Json toJson() {
        return _blockHeaderDeflater.toJson(this);
    }

    @Override
    public int hashCode() {
        final ByteArray byteArray = _blockHeaderDeflater.toBytes(this);
        return byteArray.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof BlockHeader)) { return false; }
        return Util.areEqual(this.getHash(), ((BlockHeader) object).getHash());
    }
}
