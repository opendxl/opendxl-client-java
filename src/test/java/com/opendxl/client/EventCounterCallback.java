/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.EventCallback;
import com.opendxl.client.message.Event;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Helper class allowing to count incoming events
 * For unit testing only
 */
public class EventCounterCallback implements EventCallback {
    private final Lock counterLock = new ReentrantLock();
    private final Condition counterCondition = counterLock.newCondition();
    private final AtomicInteger counter = new AtomicInteger();

    /**
     * Returns the current counter
     *
     * @return The current counter
     */
    public final int get() {
        return counter.get();
    }

    /**
     * Atomically reset the counter
     */
    final void reset() {
        counterLock.lock();
        try {
            counter.set(0);
            counterCondition.signalAll();
        } finally {
            counterLock.unlock();
        }
    }

    @Override
    public void onEvent(final Event event) {
        System.out.println(event.getDestinationChannel());
        System.out.println(new String(event.getPayload()));

        counterLock.lock();
        try {
            counter.incrementAndGet();
            counterCondition.signalAll();
        } finally {
            counterLock.unlock();
        }
    }
}
