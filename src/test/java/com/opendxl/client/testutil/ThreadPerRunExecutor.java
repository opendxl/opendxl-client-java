/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.testutil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * {@link Executor} implementation that executes the specified {@link Runnable}
 * a specified number of times using a new thread for each execution.
 * <p>
 * Note: This executor can only be used once.
 */
public class ThreadPerRunExecutor implements Executor {
    /**
     * The list of threads
     */
    private final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
    /**
     * The count of times to run the {@link Runnable}
     */
    private int runCount;

    /**
     * Constructs the executor
     *
     * @param runCount The count of times to run the {@link Runnable}
     */
    public ThreadPerRunExecutor(final int runCount) {
        this.runCount = runCount;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public synchronized void execute(final Runnable command) {
        if (this.threads.size() > 0) {
            throw new RuntimeException("Executor can only be executed once.");
        }

        for (int i = 0; i < this.runCount; i++) {
            final Thread t = new Thread(command);
            t.setDaemon(true);
            this.threads.add(t);
            t.start();
        }
    }

    /**
     * Waits for all of the threads to complete executing
     *
     * @param waitTime The maximum time to wait (milliseconds)
     * @return <code>false</code> If the maximum wait time was exceeded
     */
    public boolean joinThreads(final long waitTime) throws InterruptedException {
        final long endTime = System.currentTimeMillis() + waitTime;

        Thread t = null;
        while (this.threads.size() > 0) {
            final long joinWait = endTime - System.currentTimeMillis();
            if (joinWait > 0) {
                if (t == null) {
                    t = this.threads.remove(0);
                }

                t.join(joinWait);
                if (!t.isAlive()) {
                    t = null;
                }
            } else {
                return false;
            }
        }

        return true;
    }
}
