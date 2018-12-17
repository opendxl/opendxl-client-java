/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for creating {@link Executor} instances.
 */
class Executors {

    /** Private constructor */
    private Executors() {
        super();
    }

    /**
     * Creates a blocking queue executor that uses daemon threads. This executor differs from the standard Java
     * implementation due to the fact that it will block instead of throwing an exception when the queue is full.
     *
     * @param poolSize The pool size
     * @param maxQueueSize  The maximum queue size
     * @param threadNamePrefix The prefix for the thread name
     * @return A daemon {@link Executor}
     */
    public static ThreadPoolExecutor createDaemonExecutor(
        final int poolSize, final int maxQueueSize, final String threadNamePrefix) {
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(maxQueueSize);

        final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(
                poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, queue);

        final ThreadFactory defaultFactory = executor.getThreadFactory();
        executor.setThreadFactory(
            r -> {
                final Thread t = defaultFactory.newThread(r);
                if (threadNamePrefix != null) {
                    t.setName(threadNamePrefix + "-" + t.getId());
                }
                t.setDaemon(true);
                return t;
            }
        );

        executor.setRejectedExecutionHandler(
            (r, tpe) -> {
                try {
                    if (!tpe.isShutdown()) {
                        queue.put(r);
                    }
                } catch (InterruptedException ex) {
                    throw new RuntimeException("Error placing writer in queue", ex);
                }
            }
        );

        return executor;
    }

    /**
     * Creates a blocking queue executor that uses daemon threads. This executor differs from the standard Java
     * implementation due to the fact that it will block instead of throwing an exception when the queue is full.
     *
     * @param poolSize The pool size
     * @param maxQueueSize The maximum queue size
     * @return A daemon {@link Executor}
     */
    public static ExecutorService createDaemonExecutor(final int poolSize, final int maxQueueSize) {
        return createDaemonExecutor(poolSize, maxQueueSize, null);
    }
}
