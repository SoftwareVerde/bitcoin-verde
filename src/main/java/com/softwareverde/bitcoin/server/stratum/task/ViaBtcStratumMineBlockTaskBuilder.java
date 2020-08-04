package com.softwareverde.bitcoin.server.stratum.task;

import com.softwareverde.bitcoin.block.CanonicalMutableBlock;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ViaBtcStratumMineBlockTaskBuilder implements RelayedStratumMineBlockTaskBuilder {
    protected List<String> _merkleTreeBranches; // Little-endian merkle tree (intermediary) branch hashes...

    protected final MutableBlock _prototypeBlock = new CanonicalMutableBlock();

    protected String _extraNonce1;
    protected String _coinbaseTransactionHead;
    protected String _coinbaseTransactionTail;

    protected final ReentrantReadWriteLock.ReadLock _prototypeBlockReadLock;
    protected final ReentrantReadWriteLock.WriteLock _prototypeBlockWriteLock;

    public ViaBtcStratumMineBlockTaskBuilder(final Integer totalExtraNonceByteCount) {
        _prototypeBlock.addTransaction(new MutableTransaction());

        // NOTE: Actual nonce and timestamp are updated later within the MineBlockTask...
        _prototypeBlock.setTimestamp(0L);
        _prototypeBlock.setNonce(0L);

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _prototypeBlockReadLock = readWriteLock.readLock();
        _prototypeBlockWriteLock = readWriteLock.writeLock();
    }

    @Override
    public void setBlockVersion(final String stratumBlockVersion) {
        try {
            _prototypeBlockWriteLock.lock();

            final Long blockVersion = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumBlockVersion));
            _prototypeBlock.setVersion(blockVersion);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    @Override
    public void setPreviousBlockHash(final String stratumPreviousBlockHash) {
        try {
            _prototypeBlockWriteLock.lock();

            final Sha256Hash previousBlockHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(StratumUtil.swabHexString(stratumPreviousBlockHash)));
            _prototypeBlock.setPreviousBlockHash(previousBlockHash);

        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    @Override
    public void setExtraNonce(final String stratumExtraNonce) {
        _extraNonce1 = stratumExtraNonce;
    }

    @Override
    public void setDifficulty(final String stratumDifficulty) {
        try {
            _prototypeBlockWriteLock.lock();

            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString(stratumDifficulty));
            if (difficulty == null) { return; }

            _prototypeBlock.setDifficulty(difficulty);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    @Override
    public void setMerkleTreeBranches(final List<String> merkleTreeBranches) {
        try {
            _prototypeBlockWriteLock.lock();

            _merkleTreeBranches = merkleTreeBranches.asConst();
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

        @Override
    public void setCoinbaseTransaction(final String stratumCoinbaseTransactionHead, final String stratumCoinbaseTransactionTail) {
        try {
            _prototypeBlockWriteLock.lock();

            _coinbaseTransactionHead = stratumCoinbaseTransactionHead;
            _coinbaseTransactionTail = stratumCoinbaseTransactionTail;
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    @Override
    public StratumMineBlockTask buildMineBlockTask() {
        try {
            _prototypeBlockReadLock.lock();

            final ByteArray id = MutableByteArray.wrap(ByteUtil.integerToBytes(StratumMineBlockTaskBuilderCore.getNextId()));
            return new StratumMineBlockTask(id, _prototypeBlock, _coinbaseTransactionHead, _coinbaseTransactionTail, _extraNonce1);
        }
        finally {
            _prototypeBlockReadLock.unlock();
        }
    }
}
