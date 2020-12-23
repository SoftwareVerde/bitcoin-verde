package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;

import java.util.HashMap;

public class FakeBlockValidatorContext extends FakeUnspentTransactionOutputContext implements BlockValidator.Context {
    protected final TransactionInflaters _transactionInflaters;
    protected final VolatileNetworkTime _networkTime;
    protected final HashMap<Long, BlockHeader> _blocks = new HashMap<Long, BlockHeader>();
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<Long, MedianBlockTime>();
    protected final HashMap<Long, ChainWork> _chainWorks = new HashMap<Long, ChainWork>();

    protected final AsertReferenceBlock _asertReferenceBlock;
    private final UpgradeSchedule _upgradeSchedule;

    public FakeBlockValidatorContext(final NetworkTime networkTime, final UpgradeSchedule upgradeSchedule) {
        this(new CoreInflater(), networkTime, null, upgradeSchedule);
    }

    public FakeBlockValidatorContext(final NetworkTime networkTime, final AsertReferenceBlock asertReferenceBlock, final UpgradeSchedule upgradeSchedule) {
        this(new CoreInflater(), networkTime, asertReferenceBlock, upgradeSchedule);
    }

    public FakeBlockValidatorContext(final TransactionInflaters transactionInflaters, final NetworkTime networkTime, final AsertReferenceBlock asertReferenceBlock, final UpgradeSchedule upgradeSchedule) {
        _transactionInflaters = transactionInflaters;
        _networkTime = VolatileNetworkTimeWrapper.wrap(networkTime);
        _asertReferenceBlock = asertReferenceBlock;
        _upgradeSchedule = upgradeSchedule;
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
        final MedianBlockTime genesisMedianTimePast = MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
        this.addBlock(block, blockHeight, genesisMedianTimePast, null);
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

    @Override
    public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
        return new TransactionValidatorCore(blockOutputs, transactionValidatorContext);
    }

    @Override
    public TransactionInflater getTransactionInflater() {
        return _transactionInflaters.getTransactionInflater();
    }

    @Override
    public TransactionDeflater getTransactionDeflater() {
        return _transactionInflaters.getTransactionDeflater();
    }

    @Override
    public AsertReferenceBlock getAsertReferenceBlock() {
        return _asertReferenceBlock;
    }

    @Override
    public DifficultyCalculator newDifficultyCalculator() {
        return new DifficultyCalculator(this);
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}
