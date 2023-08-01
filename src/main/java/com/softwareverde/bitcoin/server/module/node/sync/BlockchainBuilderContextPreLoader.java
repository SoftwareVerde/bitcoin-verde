package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockchainBuilderContextPreLoader implements AutoCloseable {
    public static class BlockHeightTuple {
        public final Block block;
        public final Long blockHeight;

        public BlockHeightTuple(final Block block, final Long blockHeight) {
            this.block = block;
            this.blockHeight = blockHeight;
        }
    }

    public static class PreLoadedUnspentTransactionOutputSet extends MutableUnspentTransactionOutputSet {
        public Block block;
        public Long blockHeight;
        public Boolean isFullyLoaded = false;
        public MedianBlockTime medianBlockTime;

        public PreLoadedUnspentTransactionOutputSet(final Block block, final Long blockHeight) {
            this.block = block;
            this.blockHeight = blockHeight;
        }
    }

    protected final LoggerInstance _log = Logger.getInstance(BlockchainBuilderContextPreLoader.class);
    protected final UpgradeSchedule _upgradeSchedule;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Thread _thread;
    protected final AtomicBoolean _threadIsAlive = new AtomicBoolean(false);

    protected final SynchronousQueue<BlockHeightTuple> _pendingWork = new SynchronousQueue<>();
    protected final Container<PreLoadedUnspentTransactionOutputSet> _completedWork = new Container<>();

    public BlockchainBuilderContextPreLoader(final FullNodeDatabaseManagerFactory databaseManagerFactory, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _databaseManagerFactory = databaseManagerFactory;
        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                _threadIsAlive.set(true);

                try {
                    while (! _thread.isInterrupted()) {
                        final BlockHeightTuple workToDo = _pendingWork.take();
                        if (workToDo == null) { continue; }

                        final Block block = workToDo.block;
                        final Long blockHeight = workToDo.blockHeight;
                        final PreLoadedUnspentTransactionOutputSet unspentTransactionOutputContext = new PreLoadedUnspentTransactionOutputSet(block, blockHeight);
                        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                            final NanoTimer nanoTimer = new NanoTimer();
                            nanoTimer.start();

                            { // Load the Block's MedianBlockTime. TODO: remove after CashToken (20230515) activation.
                                final Sha256Hash blockHash = block.getHash();
                                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                                unspentTransactionOutputContext.medianBlockTime = blockHeaderDatabaseManager.getMedianBlockTime(blockId);
                            }

                            unspentTransactionOutputContext.isFullyLoaded = unspentTransactionOutputContext.quicklyLoadOutputsForBlock(databaseManager, block, blockHeight, _upgradeSchedule);

                            nanoTimer.stop();
                            if (_log.isDebugEnabled()) {
                                _log.debug("Preloaded Block " + blockHeight + " in " + nanoTimer.getMillisecondsElapsed() + "ms. isFullyLoaded=" + unspentTransactionOutputContext.isFullyLoaded);
                            }

                            synchronized (_completedWork) {
                                // wait for the previously completed work to be claimed
                                while (_completedWork.value != null) {
                                    _completedWork.wait();
                                }
                                _completedWork.value = unspentTransactionOutputContext;
                            }
                        }
                        catch (final DatabaseException exception) {
                            Logger.debug(exception);
                            return;
                        }
                    }
                }
                catch (final Exception exception) {
                    if (! (exception instanceof InterruptedException)) {
                        Logger.debug(exception);
                    }
                }
                finally {
                    _threadIsAlive.set(false);
                    _pendingWork.poll(); // ensure any race conditions between the thread dying and _pendingWork.put are resolved
                }
            }
        });
        _thread.setName("BlockchainBuilderContextPreLoader");
        _thread.start();
    }

    public UnspentTransactionOutputContext getContext(final Block previousBlock, final Block block, final Block nextBlock, final Long nextBlockHeight) throws InterruptedException {
        if (! _threadIsAlive.get()) { return null; }

        // queue the next job if it was provided
        if (nextBlock != null) {
            final BlockHeightTuple pendingWork = new BlockHeightTuple(nextBlock, nextBlockHeight);
            _pendingWork.put(pendingWork); // blocks until the previous job has completed (if it existed)
        }

        final PreLoadedUnspentTransactionOutputSet completedWork;
        synchronized (_completedWork) {
            // claim the completed work
            completedWork = _completedWork.value;
            _completedWork.value = null;
            _completedWork.notifyAll();
        }

        // if internal thread died/was not running, return null
        if (completedWork == null) { return null; }
        if (! Util.areEqual(block.getHash(), completedWork.block.getHash())) { return null; } // completed work did not match request

        // update the preloaded context with the previous block if it was not fully loaded
        if (! completedWork.isFullyLoaded) {
            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            // if the provided previous block does not match the expected previous block then return null
            if ( (previousBlock == null) || (! Util.areEqual(previousBlock.getHash(), completedWork.block.getPreviousBlockHash())) ) { return null; }

            final long previousBlockHeight = (completedWork.blockHeight - 1L);
            completedWork.update(previousBlock, previousBlockHeight, completedWork.medianBlockTime, _upgradeSchedule);

            nanoTimer.stop();
            Logger.debug("Updated preloaded block " + completedWork.blockHeight + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");

            if (! completedWork.isFullyLoaded) {
                try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    nanoTimer.start();

                    completedWork.isFullyLoaded = completedWork.finishLoadingOutputsForBlock(databaseManager, completedWork.block, completedWork.blockHeight, _upgradeSchedule);

                    nanoTimer.stop();
                    Logger.debug("Finished 2nd pass of preloading block " + completedWork.blockHeight + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
                }
            }
        }

        // return the preloaded context
        return completedWork;
    }

    @Override
    public void close() {
        _threadIsAlive.set(false); // Immediately disable future getContext requests...
        _thread.interrupt();
    }
}
