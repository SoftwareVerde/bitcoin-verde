package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
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

        public LazyLoadTask(final TaskType taskType, final Block block) {
            this.taskType = taskType;
            this.block = block;
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

                if (taskType == LazyLoadTask.TaskType.LOAD) {
                    final Boolean outputsLoadedSuccessfully = super.loadOutputsForBlock(fullNodeDatabaseManager, block);
                    if (! outputsLoadedSuccessfully) {
                        Logger.debug("Unable to lazily load outputs for Block: " + block.getHash());
                        return;
                    }
                }
                else {
                    super.update(block);
                }

                _lazyLoadTasks.removeFirst();
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    public LazyMutableUnspentTransactionOutputSet(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public synchronized Boolean loadOutputsForBlock(final FullNodeDatabaseManager databaseManager, final Block block) {
        synchronized (_lazyLoadTasks) {
            _lazyLoadTasks.addLast(new LazyLoadTask(LazyLoadTask.TaskType.LOAD, block));
        }
        return true;
    }

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        synchronized (_lazyLoadTasks) {
            if (! _lazyLoadTasks.isEmpty()) {
                _loadOutputsForBlocks();
            }

            if (! _lazyLoadTasks.isEmpty()) { return null; } // If a task failed, then return null.
        }

        return super.getUnspentTransactionOutput(transactionOutputIdentifier);
    }

    @Override
    public synchronized void update(final Block block) {
        synchronized (_lazyLoadTasks) {
            _lazyLoadTasks.addLast(new LazyLoadTask(LazyLoadTask.TaskType.UPDATE, block));
        }
    }
}
