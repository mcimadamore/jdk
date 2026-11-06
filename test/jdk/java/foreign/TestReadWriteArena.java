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
 * @summary Read ownership authorizes access, exclusive ownership authorizes close
 * @modules java.base/jdk.internal.foreign
 * @run main/othervm TestReadWriteArena
 * @run main/othervm -Xint TestReadWriteArena
 * @run main/othervm -XX:TieredStopAtLevel=1 TestReadWriteArena
 * @run main/othervm -XX:-TieredCompilation TestReadWriteArena
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jdk.internal.foreign.MemorySessionImpl;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class TestReadWriteArena {
    static class MisleadingLock extends ReentrantReadWriteLock {
        public boolean isWriteLockedByCurrentThread() { return true; }
        public int getReadHoldCount() { return 1; }
    }

    public static void main(String[] args) throws Exception {
        ReentrantReadWriteLock lock = new MisleadingLock();
        Arena arena = Arena.ofShared(lock);
        MemorySessionImpl session = MemorySessionImpl.toMemorySession(arena);
        expect(WrongThreadException.class, () -> arena.allocate(8));
        expect(WrongThreadException.class, arena::close);
        expect(WrongThreadException.class, session::acquire0);
        MemorySegment segment;
        lock.writeLock().lock();
        try {
            segment = arena.allocate(8, 4);
            segment.set(JAVA_INT, 0, 42);
        } finally {
            lock.writeLock().unlock();
        }
        expect(WrongThreadException.class, () -> segment.get(JAVA_INT, 0));
        lock.readLock().lock();
        try {
            if (segment.get(JAVA_INT, 0) != 42) throw new AssertionError();
            arena.allocate(8, 4).set(JAVA_INT, 0, 43);
            expect(WrongThreadException.class, arena::close);
            session.acquire0();
        } finally {
            lock.readLock().unlock();
        }
        // A downcall can drop its read hold, but the acquire count prevents close.
        lock.writeLock().lock();
        try {
            expect(IllegalStateException.class, arena::close);
        } finally {
            lock.writeLock().unlock();
        }
        session.release0();

        // Exercise firstReader, cachedHoldCounter, and the ThreadLocal fallback.
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<?>[] readers = new CompletableFuture<?>[3];
        for (int i = 0; i < readers.length; i++) {
            readers[i] = CompletableFuture.runAsync(() -> {
                lock.readLock().lock();
                try {
                    ready.countDown();
                    read.await();
                    if (segment.get(JAVA_INT, 0) != 42) throw new AssertionError();
                    arena.allocate(8, 4);
                    done.countDown();
                    release.await();
                } catch (InterruptedException ex) {
                    throw new AssertionError(ex);
                } finally {
                    lock.readLock().unlock();
                }
            });
        }
        ready.await();
        read.countDown();
        done.await();
        if (lock.writeLock().tryLock()) {
            lock.writeLock().unlock();
            throw new AssertionError("Writer overlapped readers");
        }
        release.countDown();
        CompletableFuture.allOf(readers).get();
        lock.writeLock().lock();
        try {
            arena.close();
            expect(IllegalStateException.class, () -> segment.get(JAVA_INT, 0));
        } finally {
            lock.writeLock().unlock();
        }
    }

    static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable ex) {
            if (type.isInstance(ex)) return;
            throw new AssertionError("Expected " + type.getName(), ex);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
