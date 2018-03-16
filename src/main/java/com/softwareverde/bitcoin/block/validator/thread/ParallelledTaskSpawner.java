package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.DatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class ParallelledTaskSpawner<T, S> {

    protected static class TaskThread<T, S> extends Thread {
        private final MysqlDatabaseConnection _databaseConnection;
        private final TaskHandler<T, S> _taskHandler;
        private final List<T> _list;
        private int _startIndex;
        private int _itemCount;

        public TaskThread(final MysqlDatabaseConnection databaseConnection, final List<T> list, final TaskHandler<T, S> taskHandler) {
            _databaseConnection = databaseConnection;
            _list = list;
            _taskHandler = taskHandler;
        }

        public void setStartIndex(final int startIndex) {
            _startIndex = startIndex;
        }

        public void setItemCount(final int itemCount) {
            _itemCount = itemCount;
        }

        @Override
        public void run() {
            try {
                _taskHandler.init(_databaseConnection);

                for (int j = 0; j < _itemCount; ++j) {
                    final T item = _list.get(_startIndex + j);
                    _taskHandler.executeTask(item);
                }
            }
            catch (final Exception exception) {
                Logger.log(exception);
            }
            finally {
                try { _databaseConnection.close(); } catch (final Exception exception) { }
            }
        }

        public S getResult() {
            return _taskHandler.getResult();
        }
    }

    protected static void _closeConnection(final MysqlDatabaseConnection databaseConnection) {
        try {
            if (databaseConnection != null) {
                databaseConnection.close();
            }
        }
        catch (DatabaseException exception) { }
    }

    protected static void _closeConnections(final MysqlDatabaseConnection[] databaseConnections) {
        for (final MysqlDatabaseConnection databaseConnection : databaseConnections) {
            _closeConnection(databaseConnection);
        }
    }

    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected List<TaskThread<T, S>> _taskThreads = null;
    protected TaskHandlerFactory<T, S> _taskHandlerFactory;

    public void setTaskHandler(final TaskHandlerFactory<T, S> taskHandlerFactory) {
        _taskHandlerFactory = taskHandlerFactory;
    }

    public ParallelledTaskSpawner(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void executeTasks(final List<T> items, final int maxThreadCount) {
        final int totalItemCount = items.getSize();
        final int threadCount = Math.min(maxThreadCount, Math.max(1, (totalItemCount / maxThreadCount)));
        final int itemsPerThread = (totalItemCount / threadCount);

        final MysqlDatabaseConnection[] mysqlDatabaseConnections = new MysqlDatabaseConnection[threadCount];
        for (int i=0; i<threadCount; ++i) {
            try {
                final MysqlDatabaseConnection mysqlDatabaseConnection = _databaseConnectionFactory.newConnection();
                mysqlDatabaseConnection.executeSql(new Query("SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"));
                mysqlDatabaseConnections[i] = mysqlDatabaseConnection;
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                _closeConnections(mysqlDatabaseConnections);
                return;
            }
        }

        final ImmutableListBuilder<TaskThread<T, S>> listBuilder = new ImmutableListBuilder<TaskThread<T, S>>(threadCount);

        for (int i = 0; i < threadCount; ++i) {
            final int startIndex = i * itemsPerThread;
            final int remainingItems = (items.getSize() - startIndex);
            final int itemCount = ( (i < (threadCount - 1)) ? Math.min(itemsPerThread, remainingItems) : remainingItems);

            final MysqlDatabaseConnection databaseConnection = mysqlDatabaseConnections[i];
            final TaskThread<T, S> taskThread = new TaskThread<T, S>(databaseConnection, items, _taskHandlerFactory.newInstance());
            taskThread.setStartIndex(startIndex);
            taskThread.setItemCount(itemCount);
            taskThread.start();

            listBuilder.add(taskThread);
        }

        _taskThreads = listBuilder.build();
    }

    public List<S> waitForResults() {
        final ImmutableListBuilder<S> listBuilder = new ImmutableListBuilder<S>();

        for (int i = 0; i < _taskThreads.getSize(); ++i) {
            final TaskThread<T, S> taskThread = _taskThreads.get(i);

            try { taskThread.join(); } catch (final Exception exception) { }

            listBuilder.add(taskThread.getResult());
        }
        return listBuilder.build();
    }
}
