package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.stratum.database.WorkerDatabaseManager;
import com.softwareverde.bitcoin.server.module.stratum.database.WorkerShare;
import com.softwareverde.concurrent.ConcurrentHashSet;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerShareQueue {
    protected final Integer MAX_WORKER_ID_CACHE_SIZE = 1048576; // Arbitrary upper bound to prevent OOM/DOS.
    protected final ConcurrentHashMap<String, WorkerId> _workerIdCache = new ConcurrentHashMap<>();
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final ConcurrentHashSet<WorkerShare> _workerShares = new ConcurrentHashSet<>();
    protected final Thread _flushThread;
    protected final Thread _cacheThread;
    protected volatile Boolean _isStarted = false;

    protected synchronized WorkerId _getWorkerId(final String workerUsername) {
        final WorkerId cachedWorkerId = _workerIdCache.get(workerUsername);
        if (cachedWorkerId != null) { return cachedWorkerId; }

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final WorkerDatabaseManager workerDatabaseManager = new WorkerDatabaseManager(databaseConnection);
            final WorkerId workerId = workerDatabaseManager.getWorkerId(workerUsername);
            if (workerId == null) { return null; }

            final Boolean workerHasBeenDeleted = workerDatabaseManager.hasWorkedBeenDeleted(workerId);
            if (workerHasBeenDeleted) { return null; }

            if (_workerIdCache.size() >= MAX_WORKER_ID_CACHE_SIZE) {
                _workerIdCache.clear();
            }
            _workerIdCache.put(workerUsername, workerId);
            return workerId;
        }
        catch (final DatabaseException exception) {
            Logger.debug("Unable to load WorkerId for: " + workerUsername, exception);
            return null;
        }
    }

    protected void _drainWorkerShareQueue() {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final WorkerDatabaseManager workerDatabaseManager = new WorkerDatabaseManager(databaseConnection);

            final int maxBatchSize = 1024;
            while (true) {
                final MutableList<WorkerShare> workerShares = new MutableList<>(1024);

                final Iterator<WorkerShare> iterator = _workerShares.iterator();
                while (iterator.hasNext()) {
                    final WorkerShare workerShare = iterator.next();
                    iterator.remove();

                    workerShares.add(workerShare);
                    if (workerShares.getCount() >= maxBatchSize) { break; }
                }
                if (workerShares.isEmpty()) { break; }

                workerDatabaseManager.addWorkerShares(workerShares);

                // Prevent endlessly storing partial batches...
                if (workerShares.getCount() < maxBatchSize) { break; }
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    public WorkerShareQueue(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;

        _flushThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final NanoTimer nanoTimer = new NanoTimer();

                final Long targetSleepTimeMs = 5000L;
                final Thread thread = Thread.currentThread();
                while (! thread.isInterrupted()) {
                    nanoTimer.start();
                    _drainWorkerShareQueue();
                    nanoTimer.stop();

                    final long sleepTime = (long) (targetSleepTimeMs - nanoTimer.getMillisecondsElapsed());
                    if (sleepTime > 0L) {
                        try { Thread.sleep(sleepTime); }
                        catch (final InterruptedException exception) { break; }
                    }
                }

                // Empty the queue before exiting...
                _drainWorkerShareQueue();
            }
        });
        _flushThread.setName("WorkerShareQueue Flush Thread");
        _flushThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        _flushThread.setDaemon(true);

        _cacheThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final long sleepTimeMs = 150000L; // 2.5 minutes...
                final Thread thread = Thread.currentThread();
                while (! thread.isInterrupted()) {
                    if (! _workerIdCache.isEmpty()) {
                        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                            final WorkerDatabaseManager workerDatabaseManager = new WorkerDatabaseManager(databaseConnection);

                            final Iterator<Map.Entry<String, WorkerId>> iterator = _workerIdCache.entrySet().iterator();
                            while (iterator.hasNext() && (! thread.isInterrupted())) {
                                final Map.Entry<String, WorkerId> entry = iterator.next();
                                final WorkerId workerId = entry.getValue();
                                final Boolean workerHasBeenDeleted = workerDatabaseManager.hasWorkedBeenDeleted(workerId);
                                if (workerHasBeenDeleted) {
                                    iterator.remove();
                                }
                            }
                        }
                        catch (final DatabaseException exception) {
                            Logger.debug(exception);
                        }
                    }

                    try { Thread.sleep(sleepTimeMs); }
                    catch (final InterruptedException exception) { break; }
                }
            }
        });
        _cacheThread.setName("WorkerShareQueue Cache Thread");
        _cacheThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        _cacheThread.setDaemon(true);
    }

    public Boolean addWorkerShare(final String workerUsername, final Long shareDifficulty, final Sha256Hash blockHash) {
        if (! _isStarted) {
            Logger.info("Attempted to add worker share while queue is inactive: " + workerUsername + " " + shareDifficulty + " " + blockHash);
            return false;
        }

        final WorkerId workerId = _getWorkerId(workerUsername);
        if (workerId == null) {
            Logger.warn("Unable to add worker share: " + workerUsername + " " + shareDifficulty + " " + blockHash);
            return false;
        }

        final WorkerShare workerShare = new WorkerShare(workerId, shareDifficulty, blockHash);
        return _workerShares.add(workerShare);
    }

    public void start() {
        _isStarted = true;
        _cacheThread.start();
        _flushThread.start();
    }

    public void stop() {
        _isStarted = false;

        _cacheThread.interrupt();
        try {
            _cacheThread.join();
        }
        catch (final InterruptedException exception) { }

        _flushThread.interrupt();
        try {
            _flushThread.join();
        }
        catch (final InterruptedException exception) { }
    }
}
