/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.openjdk.jmh.annotations.*;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
public class LoopOverNonConstantReadWrite extends JavaLayouts {
    static final int ELEM_SIZE = 1_000_000;
    @Param({"SHARED", "READ_WRITE"}) public String kind;
    @Param({"FIRST", "CACHED", "THREAD_LOCAL", "WRITE"}) public String reader;
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    final CountDownLatch stop = new CountDownLatch(1);
    final List<Thread> holders = new ArrayList<>();
    Arena arena;
    MemorySegment segment;

    void addReader() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        Thread holder = Thread.ofPlatform().start(() -> {
            lock.readLock().lock();
            try {
                ready.countDown();
                stop.await();
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            } finally {
                lock.readLock().unlock();
            }
        });
        holders.add(holder);
        ready.await();
    }

    @Setup
    public void setup() throws InterruptedException {
        arena = kind.equals("READ_WRITE") ? Arena.ofShared(lock) : Arena.ofShared();
        lock.writeLock().lock();
        try {
            segment = arena.allocate((long) ELEM_SIZE * Integer.BYTES, Integer.BYTES);
            for (int i = 0; i < ELEM_SIZE; i++) segment.setAtIndex(JAVA_INT, i, i);
        } finally {
            lock.writeLock().unlock();
        }
        if (reader.equals("WRITE")) {
            lock.writeLock().lock();
        } else {
            if (!reader.equals("FIRST")) addReader();
            lock.readLock().lock();
            if (reader.equals("THREAD_LOCAL")) addReader();
        }
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        if (!reader.equals("WRITE")) {
            lock.readLock().unlock();
            stop.countDown();
            for (Thread holder : holders) holder.join();
            lock.writeLock().lock();
        }
        try {
            arena.close();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int segment_get() {
        return (int) VH_INT.get(segment, 0L);
    }

    @Benchmark
    public int segment_loop() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) sum += (int) VH_INT.get(segment, (long) i);
        return sum;
    }
}
