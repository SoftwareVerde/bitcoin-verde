package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class MockBlockchain extends Blockchain {
    protected final HashMap<Long, MedianBlockTime> _fakeMedianBlockTimes = new HashMap<>();
    protected final HashMap<Sha256Hash, Long> _blockHeights = new HashMap<>();

    public MockBlockchain(final BlockStore blockStore) {
        super(blockStore);
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        if (! _fakeMedianBlockTimes.containsKey(blockHeight)) {
            System.out.println("Requested non-existent Median Block Time: " + blockHeight);
        }

        return _fakeMedianBlockTimes.get(blockHeight);
    }

    public void setMedianBlockTime(final Long blockHeight, final MedianBlockTime medianBlockTime) {
        _fakeMedianBlockTimes.put(blockHeight, medianBlockTime);
    }

    public void defineBlock(final Sha256Hash blockHash, final Long blockHeight) {
        _blockHeights.put(blockHash, blockHeight);
    }

    @Override
    public Long getBlockHeight(final Sha256Hash blockHash) {
        if (_blockHeights.containsKey(blockHash)) {
            return _blockHeights.get(blockHash);
        }

        System.out.println("Requested non-existent BlockHeight: " + blockHash);
        return super.getBlockHeight(blockHash);
    }
}
