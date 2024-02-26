/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.threads.internal;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.threads.CoreEventLoop;
import net.openhft.chronicle.threads.ThreadHolder;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * <h1>EventLoopThreadHolder</h1>
 * <h2>Notes on printBlockTime</h2>
 * <ul>
 *     <li>{@link #printBlockTimeNS} defines the next time at which a thread dump can be reported</li>
 *     <li>{@link #printBlockTimeNS} is mutated on every dump and will be set to {@code min(1.41d * intervalToAddNS, 20d * monitorIntervalNS)}</li>
 *     <li>In other words: {@link #printBlockTimeNS} will grow by the lesser of 41% or {@code 20d * monitorIntervalNS}</li>
 *     <li>{@link #printBlockTimeNS} will be reset to {@link #monitorIntervalNS} if {@link #resetTimers()} is called</li>
 *     <li>If {@link #resetTimers()} is not called then {@link #printBlockTimeNS} will grow continuously</li>
 * </ul>
 */
public class EventLoopThreadHolder implements ThreadHolder {
    private final CoreEventLoop eventLoop;
    private final long monitorIntervalNS;
    private long intervalToAddNS;
    private long printBlockTimeNS;

    public EventLoopThreadHolder(long monitorIntervalNS, CoreEventLoop eventLoop) {
        this.monitorIntervalNS = intervalToAddNS = printBlockTimeNS = monitorIntervalNS;
        this.eventLoop = eventLoop;
    }

    @Override
    public boolean isAlive() {
        return eventLoop.isAlive();
    }

    @Override
    public void reportFinished() {
        Jvm.warn().on(getClass(), "Monitoring a task which has finished " + eventLoop);
    }

    @Override
    public long startedNS() {
        return eventLoop.loopStartNS();
    }

    @Override
    public void resetTimers() {
        intervalToAddNS =
                printBlockTimeNS = monitorIntervalNS;
    }

    @Override
    public boolean shouldLog(long nowNS) {
        long blockingTimeNS = nowNS - startedNS();
        return blockingTimeNS >= printBlockTimeNS;
    }

    @Override
    public void dumpThread(long startedNS, long nowNS) {
        long blockingTimeNS = nowNS - startedNS;
        double blockingTimeMS = blockingTimeNS / 100_000 / 10.0;
        if (blockingTimeMS <= 0.0)
            return;
        eventLoop.dumpRunningState(eventLoop.name() + " thread has blocked for "
                        + blockingTimeMS + " ms.",
                // check we are still in the loop.
                () -> eventLoop.loopStartNS() == startedNS);

        printBlockTimeNS += intervalToAddNS;
        intervalToAddNS = (long) Math.min(1.41d * intervalToAddNS, 20d * monitorIntervalNS);
    }

    @VisibleForTesting
    protected long getPrintBlockTimeNS() {
        return printBlockTimeNS;
    }

    @Override
    public long timingToleranceNS() {
        return monitorIntervalNS + timingErrorNS();
    }

    protected long timingErrorNS() {
        return TIMING_ERROR;
    }

    @Override
    public String getName() {
        return eventLoop.name();
    }

    @Override
    public void monitorThreadDelayed(long actionCallDelayNS) {
        // report it??
    }
}
