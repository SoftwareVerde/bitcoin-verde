package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.DatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class ParallelledTaskSpawner<T> {
    public interface TaskHandler<T, S> {
        void init(MysqlDatabaseConnection databaseConnection);
        void executeTask(T item);
        S getResult();
    }

    protected static class TaskThread<T> extends Thread {
        private final MysqlDatabaseConnection _databaseConnection;
        private final TaskHandler<T, ?> _taskHandler;
        private final List<T> _list;
        private int _startIndex;
        private int _itemCount;

        public TaskThread(final MysqlDatabaseConnection databaseConnection, final List<T> list, final TaskHandler<T, ?> taskHandler) {
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
                Logger.log(Thread.currentThread().getId() + " completed. Closing connection.");
                try { _databaseConnection.close(); } catch (final Exception exception) { }
            }
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
    protected TaskHandler<T, ?> _taskHandler;
    protected TaskThread[] _taskThreads = new TaskThread[0];

    public void setTaskHandler(final TaskHandler<T, ?> taskHandler) {
        _taskHandler = taskHandler;
    }

    public ParallelledTaskSpawner(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void executeTasks(final List<T> items, final int maxThreadCount) {
        final int totalItemCount = items.getSize();
        final int threadCount = Math.min(maxThreadCount, Math.max(1, (totalItemCount / maxThreadCount)));
        final int itemsPerThread = (totalItemCount / threadCount);

        _taskThreads = new TaskThread[threadCount];

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

        for (int i = 0; i < threadCount; ++i) {
            final int startIndex = i * itemsPerThread;
            final int remainingItems = (items.getSize() - startIndex);
            final int itemCount = ( (i < (threadCount - 1)) ? Math.min(itemsPerThread, remainingItems) : remainingItems);

            final MysqlDatabaseConnection databaseConnection = mysqlDatabaseConnections[i];
            final TaskThread<T> taskThread = new TaskThread<T>(databaseConnection, items, _taskHandler);
            taskThread.setStartIndex(startIndex);
            taskThread.setItemCount(itemCount);

            _taskThreads[i] = taskThread;
        }

        for (int i = 0; i < threadCount; ++i) {
            _taskThreads[i].start();
        }
    }

    public void waitUntilComplete() {
        for (int i = 0; i < _taskThreads.length; ++i) {
            try { _taskThreads[i].join(); } catch (final Exception exception) { }
        }
    }
}
