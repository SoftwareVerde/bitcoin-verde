package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class FakeBlockValidatorContext extends FakeUnspentTransactionOutputContext implements BlockValidator.Context {
    protected final VolatileNetworkTime _networkTime;
    protected final HashMap<Long, BlockHeader> _blocks = new HashMap<Long, BlockHeader>();
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<Long, MedianBlockTime>();
    protected final HashMap<Long, ChainWork> _chainWorks = new HashMap<Long, ChainWork>();

    public FakeBlockValidatorContext(final NetworkTime networkTime) {
        _networkTime = VolatileNetworkTimeWrapper.wrap(networkTime);
    }

    public void addBlockHeader(final BlockHeader block, final Long blockHeight) {
        this.addBlockHeader(block, blockHeight, null, null);
    }

    public void addBlockHeader(final BlockHeader block, final Long blockHeight, final MedianBlockTime medianBlockTime, final ChainWork chainWork) {
        _blocks.put(blockHeight, block);
        _chainWorks.put(blockHeight, chainWork);
        _medianBlockTimes.put(blockHeight, medianBlockTime);
    }

    public void addBlock(final Block block, final Long blockHeight) {
        this.addBlock(block, blockHeight, null, null);
    }

    public void addBlock(final Block block, final Long blockHeight, final MedianBlockTime medianBlockTime, final ChainWork chainWork) {
        this.addBlockHeader(block, blockHeight, medianBlockTime, chainWork);

        final Sha256Hash blockHash = block.getHash();
        boolean isCoinbase = true;
        for (final Transaction transaction : block.getTransactions()) {
            super.addTransaction(transaction, blockHash, blockHeight, isCoinbase);
            isCoinbase = false;
        }
    }

    @Override
    public VolatileNetworkTime getNetworkTime() {
        return _networkTime;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        if (! _medianBlockTimes.containsKey(blockHeight)) {
            Logger.debug("Requested non-existent MedianBlockTime: " + blockHeight, new Exception());
        }

        return _medianBlockTimes.get(blockHeight);
    }

    @Override
    public ChainWork getChainWork(final Long blockHeight) {
        if (! _chainWorks.containsKey(blockHeight)) {
            Logger.debug("Requested non-existent ChainWork: " + blockHeight, new Exception());
        }

        return _chainWorks.get(blockHeight);
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        if (! _medianBlockTimes.containsKey(blockHeight)) {
            Logger.debug("Requested non-existent BlockHeader: " + blockHeight, new Exception());
        }

        return _blocks.get(blockHeight);
    }
}
