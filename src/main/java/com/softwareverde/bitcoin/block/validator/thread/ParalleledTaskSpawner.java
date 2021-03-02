package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;

public class ParalleledTaskSpawner<T, S> {
    protected final String _name;
    protected final ThreadPool _threadPool;
    protected List<ValidationTask<T, S>> _validationTasks = null;
    protected TaskHandlerFactory<T, S> _taskHandlerFactory;

    public void setTaskHandlerFactory(final TaskHandlerFactory<T, S> taskHandlerFactory) {
        _taskHandlerFactory = taskHandlerFactory;
    }

    public ParalleledTaskSpawner(final String name, final ThreadPool threadPool) {
        _name = name;
        _threadPool = threadPool;
    }

    public void executeTasks(final List<T> items, final int maxThreadCount) {
        final int totalItemCount = items.getCount();
        final int threadCount = Math.min(maxThreadCount, Math.max(1, (totalItemCount / maxThreadCount)));
        final int itemsPerThread = (totalItemCount / threadCount);

        final ImmutableListBuilder<ValidationTask<T, S>> listBuilder = new ImmutableListBuilder<ValidationTask<T, S>>(threadCount);

        for (int i = 0; i < threadCount; ++i) {
            final int startIndex = i * itemsPerThread;
            final int remainingItems = (totalItemCount - startIndex);
            final int itemCount = ( (i < (threadCount - 1)) ? Math.min(itemsPerThread, remainingItems) : remainingItems);
            if (itemCount < 1) { break; }

            final ValidationTask<T, S> validationTask = new ValidationTask<T, S>(_name, items, _taskHandlerFactory.newInstance());
            validationTask.setStartIndex(startIndex);
            validationTask.setItemCount(itemCount);
            validationTask.enqueueTo(_threadPool);
            listBuilder.add(validationTask);
        }

        _validationTasks = listBuilder.build();
    }

    public List<S> waitForResults() {
        final ImmutableListBuilder<S> listBuilder = new ImmutableListBuilder<S>();

        for (int i = 0; i < _validationTasks.getCount(); ++i) {
            final ValidationTask<T, S> validationTask = _validationTasks.get(i);
            final S result = validationTask.getResult();
            if (result == null) { return null; }

            listBuilder.add(result);
        }
        return listBuilder.build();
    }

    public void abort() {
        for (int i = 0; i < _validationTasks.getCount(); ++i) {
            final ValidationTask<T, S> validationTask = _validationTasks.get(i);
            validationTask.abort();
        }
    }
}
