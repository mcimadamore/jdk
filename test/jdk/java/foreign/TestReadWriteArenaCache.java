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

/*
 * @test
 * @summary RRWL arena ownership caching respects unlock, nesting, and virtual threads
 * @run main/othervm TestReadWriteArenaCache
 * @run main/othervm -Xint TestReadWriteArenaCache
 * @run main/othervm -XX:TieredStopAtLevel=1 TestReadWriteArenaCache
 * @run main/othervm -XX:-TieredCompilation TestReadWriteArenaCache
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class TestReadWriteArenaCache {
    public static void main(String[] args) throws Exception {
        for (boolean virtual : new boolean[] { false, true }) {
            FutureTask<Void> task = new FutureTask<>(() -> {
                singleLock();
                manyLocks();
                writeAndCondition();
                return null;
            });
            Thread thread = virtual ? Thread.ofVirtual().unstarted(task)
                    : Thread.ofPlatform().unstarted(task);
            thread.start();
            task.get();
            thread.join();
        }
    }

    static void singleLock() throws Exception {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        Arena arena = Arena.ofShared(lock);
        MemorySegment segment;
        lock.readLock().lock();
        try { segment = arena.allocate(4, 4); }
        finally { lock.readLock().unlock(); }
        expectDenied(segment);
        for (int mode = 0; mode < 4; mode++) {
            switch (mode) {
                case 0 -> lock.readLock().lock();
                case 1 -> lock.readLock().lockInterruptibly();
                case 2 -> {
                    if (!lock.readLock().tryLock()) throw new AssertionError();
                }
                case 3 -> {
                    if (!lock.readLock().tryLock(1, java.util.concurrent.TimeUnit.SECONDS))
                        throw new AssertionError();
                }
            }
            try { segment.get(JAVA_INT, 0); }
            finally { lock.readLock().unlock(); }
            expectDenied(segment);
        }
        lock.writeLock().lock();
        try { arena.close(); }
        finally { lock.writeLock().unlock(); }
    }

    static void manyLocks() {
        int count = 100;
        ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[count];
        Arena[] arenas = new Arena[count];
        MemorySegment[] segments = new MemorySegment[count];
        try {
            for (int i = 0; i < count; i++) {
                locks[i] = new ReentrantReadWriteLock();
                arenas[i] = Arena.ofShared(locks[i]);
                locks[i].readLock().lock();
                segments[i] = arenas[i].allocate(4, 4);
                segments[i].set(JAVA_INT, 0, i);
            }
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < count; i++) {
                    if (segments[i].get(JAVA_INT, 0) != i) throw new AssertionError();
                }
                Thread.yield(); // A virtual thread may resume on a different carrier.
            }
            // Reentrant unlock must not revoke an outer read hold.
            locks[0].readLock().lock();
            segments[0].get(JAVA_INT, 0);
            locks[0].readLock().unlock();
            segments[0].get(JAVA_INT, 0);
            // Matching final unlock must invalidate a cached positive result.
            locks[0].readLock().unlock();
            expectDenied(segments[0]);
            // Releasing a different lock must not make either stale ownership valid.
            segments[99].get(JAVA_INT, 0);
            locks[1].readLock().unlock();
            expectDenied(segments[1]);
            segments[99].get(JAVA_INT, 0);
            locks[99].readLock().unlock();
            expectDenied(segments[99]);
        } finally {
            for (int i = 0; i < count; i++) {
                if (locks[i] == null) continue;
                while (locks[i].getReadHoldCount() != 0) locks[i].readLock().unlock();
                if (arenas[i] != null) {
                    locks[i].writeLock().lock();
                    try { arenas[i].close(); }
                    finally { locks[i].writeLock().unlock(); }
                }
            }
        }
    }

    static void writeAndCondition() throws Exception {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        Arena arena = Arena.ofShared(lock);
        lock.writeLock().lock();
        try {
            MemorySegment segment = arena.allocate(4, 4);
            segment.set(JAVA_INT, 0, 42);
            lock.writeLock().newCondition().awaitNanos(1);
            if (segment.get(JAVA_INT, 0) != 42) throw new AssertionError();
            lock.readLock().lock();
            lock.writeLock().unlock();
            try {
                segment.get(JAVA_INT, 0); // Downgrade leaves a valid read hold.
            } finally {
                lock.readLock().unlock();
            }
            expectDenied(segment);
            lock.writeLock().lock();
        } finally {
            try { arena.close(); }
            finally { lock.writeLock().unlock(); }
        }
    }

    static void expectDenied(MemorySegment segment) {
        try { segment.get(JAVA_INT, 0); }
        catch (WrongThreadException expected) { return; }
        throw new AssertionError("Stale cached ownership survived unlock");
    }
}
