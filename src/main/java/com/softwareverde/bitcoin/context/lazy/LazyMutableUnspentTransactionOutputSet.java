package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.context.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
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

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final LinkedList<LazyLoadTask> _lazyLoadTasks = new LinkedList<LazyLoadTask>();

    /**
     * Empties the LazyTasks, if there any.
     *  Requires synchronization on _lazyLoadTasks.
     *  If a task fails, the function aborts and _lazyLoadTasks is not emptied.
     */
    protected void _loadOutputsForBlocks() {
        try (final FullNodeDatabaseManager fullNodeDatabaseManager = _databaseManagerFactory.newDatabaseManager()) {
            while (! _lazyLoadTasks.isEmpty()) { // NOTE: Constant time for LinkedList...
                final LazyLoadTask lazyLoadTask = _lazyLoadTasks.peekFirst();

                final Block block = lazyLoadTask.block;
                final LazyLoadTask.TaskType taskType = lazyLoadTask.taskType;
                final Long blockHeight = lazyLoadTask.blockHeight;

                if (taskType == LazyLoadTask.TaskType.LOAD) {
                    final Boolean outputsLoadedSuccessfully = super.loadOutputsForBlock(fullNodeDatabaseManager, block, blockHeight);
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
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    public LazyMutableUnspentTransactionOutputSet() {
        this(null);
    }

    public LazyMutableUnspentTransactionOutputSet(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
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
                _loadOutputsForBlocks();
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
