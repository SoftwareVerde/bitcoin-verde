package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public interface TaskHandler<T, S> {
    void init(MysqlDatabaseConnection databaseConnection);
    void executeTask(T item);
    S getResult();
}
