package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.LinkedList;

public class LazyMutableUnspentTransactionOutputSet extends MutableUnspentTransactionOutputSet {
    protected static class LazyLoadTask {
        enum TaskType { LOAD, UPDATE }

        final Block block;
        final TaskType taskType;
        final Long blockHeight;

        public LazyLoadTask(final TaskType taskType, final Block block, final Long blockHeight) {
            this.taskType = taskType;
            this.block = block;
            this.blockHeight = blockHeight;
        }
    }

    protected final FullNodeDatabaseManager _databaseManager;
    protected final LinkedList<LazyLoadTask> _lazyLoadTasks = new LinkedList<LazyLoadTask>();

    /**
     * Empties the LazyTasks, if there any.
     *  Requires synchronization on _lazyLoadTasks.
     *  If a task fails, the function aborts and _lazyLoadTasks is not emptied.
     */
    protected void _loadOutputsForBlocks() throws DatabaseException {
        while (! _lazyLoadTasks.isEmpty()) { // NOTE: Constant time for LinkedList...
            final LazyLoadTask lazyLoadTask = _lazyLoadTasks.peekFirst();

            final Block block = lazyLoadTask.block;
            final LazyLoadTask.TaskType taskType = lazyLoadTask.taskType;

            final Long blockHeight;
            if (lazyLoadTask.blockHeight != null) {
                blockHeight = lazyLoadTask.blockHeight;
            }
            else {
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
                final Sha256Hash previousBlockHash = block.getPreviousBlockHash();
                final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(previousBlockHash);
                final Long previousBlockHeight = blockHeaderDatabaseManager.getBlockHeight(previousBlockId);
                if (previousBlockHeight == null) {
                    Logger.debug("Unable to lazily load blockHeight for Block: " + block.getHash());
                    return;
                }

                blockHeight = (previousBlockHeight + 1L);
            }

            if (taskType == LazyLoadTask.TaskType.LOAD) {
                final Boolean outputsLoadedSuccessfully = super.loadOutputsForBlock(_databaseManager, block, blockHeight);
                if (! outputsLoadedSuccessfully) {
                    Logger.debug("Unable to lazily load outputs for Block: " + block.getHash());
                    return;
                }
            }
            else {
                super.update(block, blockHeight);
            }

            _lazyLoadTasks.removeFirst();
        }
    }

    public LazyMutableUnspentTransactionOutputSet(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public synchronized Boolean loadOutputsForBlock(final FullNodeDatabaseManager databaseManager, final Block block, final Long blockHeight) {
        synchronized (_lazyLoadTasks) {
            _lazyLoadTasks.addLast(new LazyLoadTask(LazyLoadTask.TaskType.LOAD, block, blockHeight));
        }
        return true;
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        synchronized (_lazyLoadTasks) {
            if (! _lazyLoadTasks.isEmpty()) {
                try {
                    _loadOutputsForBlocks();
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
                    return null;
                }
            }

            if (! _lazyLoadTasks.isEmpty()) { return null; } // If a task failed, then return null.
        }

        return super.getTransactionOutput(transactionOutputIdentifier);
    }

    @Override
    public synchronized void update(final Block block, final Long blockHeight) {
        synchronized (_lazyLoadTasks) {
            _lazyLoadTasks.addLast(new LazyLoadTask(LazyLoadTask.TaskType.UPDATE, block, blockHeight));
        }
    }

    @Override
    public synchronized void clear() {
        _lazyLoadTasks.clear();
        super.clear();
    }
}
