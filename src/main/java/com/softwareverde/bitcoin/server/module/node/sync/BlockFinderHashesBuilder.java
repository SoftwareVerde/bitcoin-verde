package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

public class BlockFinderHashesBuilder {
    protected final Blockchain _blockchain;

    protected List<Sha256Hash> _createBlockFinderBlockHashes(final Boolean processedBlocksOnly, final Integer offset) {
        final Sha256Hash headBlockHash;
        {
            final Sha256Hash blockHash;
            if (processedBlocksOnly) {
                blockHash = _blockchain.getHeadBlockHash();
            }
            else {
                blockHash = _blockchain.getHeadBlockHeaderHash();
            }

            final BlockHeader blockHeader = _blockchain.getParentBlockHeader(blockHash, offset);
            headBlockHash = (blockHeader != null ? blockHeader.getHash() : null);
        }

        final Long maxBlockHeight = Util.coalesce(_blockchain.getBlockHeight(headBlockHash));

        final MutableList<Sha256Hash> blockHashes = new MutableArrayList<>(BitcoinUtil.log2(maxBlockHeight.intValue()) + 11);
        int blockHeightStep = 1;
        for (long blockHeight = maxBlockHeight; blockHeight >= 0L; blockHeight -= blockHeightStep) {
            final Sha256Hash blockHash = _blockchain.getBlockHash(blockHeight);
            blockHashes.add(blockHash);

            if (blockHashes.getCount() >= 10) {
                blockHeightStep *= 2;
            }
        }

        return blockHashes;
    }

    public BlockFinderHashesBuilder(final Blockchain blockchain) {
        _blockchain = blockchain;
    }

    public List<Sha256Hash> createBlockFinderBlockHashes() {
        return _createBlockFinderBlockHashes(true, 0);
    }

    public List<Sha256Hash> createBlockFinderBlockHashes(final Integer offset) {
        return _createBlockFinderBlockHashes(true, offset);
    }

    public List<Sha256Hash> createBlockHeaderFinderBlockHashes() {
        return _createBlockFinderBlockHashes(false, 0);
    }

    public List<Sha256Hash> createBlockHeaderFinderBlockHashes(final Integer offset) {
        return _createBlockFinderBlockHashes(false, offset);
    }
}
