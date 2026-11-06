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
 * @summary Mutex arenas check the trusted synchronizer, ignoring overridable lock queries
 * @modules java.base/jdk.internal.foreign
 * @run junit/othervm TestReadWriteArenaOwnership
 * @run junit/othervm -Xint TestReadWriteArenaOwnership
 * @run junit/othervm -XX:TieredStopAtLevel=1 TestReadWriteArenaOwnership
 * @run junit/othervm -XX:-TieredCompilation TestReadWriteArenaOwnership
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jdk.internal.foreign.MemorySessionImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestReadWriteArenaOwnership {
    static class MisleadingLock extends ReentrantReadWriteLock {
        final boolean answer;

        MisleadingLock(boolean answer) {
            this.answer = answer;
        }

        @Override
        public boolean isWriteLockedByCurrentThread() {
            return answer;
        }

        @Override
        protected Thread getOwner() {
            return answer ? Thread.currentThread() : null;
        }
    }

    @Test
    public void cannotForgeOwnership() {
        ReentrantReadWriteLock lock = new MisleadingLock(true);
        Arena arena = Arena.ofShared(lock);
        MemorySessionImpl session = MemorySessionImpl.toMemorySession(arena);
        assertTrue(lock.isWriteLockedByCurrentThread());
        assertThrows(WrongThreadException.class, () -> arena.allocate(8));
        assertThrows(WrongThreadException.class, arena::close);
        assertThrows(WrongThreadException.class, session::acquire0);

        MemorySegment segment;
        ByteBuffer buffer;
        lock.writeLock().lock();
        try {
            segment = arena.allocate(8);
            buffer = segment.asByteBuffer();
            assertTrue(segment.isAccessibleBy(Thread.currentThread()));
        } finally {
            lock.writeLock().unlock();
        }
        try {
            assertFalse(segment.isAccessibleBy(Thread.currentThread()));
            assertFalse(session.isCloseableBy(Thread.currentThread()));
            assertThrows(WrongThreadException.class, () -> segment.get(ValueLayout.JAVA_BYTE, 0));
            assertThrows(WrongThreadException.class, () -> segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 1));
            assertThrows(WrongThreadException.class, () -> segment.fill((byte) 1));
            assertThrows(WrongThreadException.class, () -> buffer.get(0));
            assertThrows(WrongThreadException.class, session::acquire0);
            assertThrows(WrongThreadException.class, arena::close);
            assertTrue(arena.scope().isAlive());
        } finally {
            lock.writeLock().lock();
            try {
                arena.close();
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    @Test
    public void cannotHideOwnership() {
        ReentrantReadWriteLock lock = new MisleadingLock(false);
        Arena arena = Arena.ofShared(lock);
        MemorySessionImpl session = MemorySessionImpl.toMemorySession(arena);
        lock.writeLock().lock();
        try {
            assertFalse(lock.isWriteLockedByCurrentThread());
            MemorySegment segment = arena.allocate(8);
            assertTrue(segment.isAccessibleBy(Thread.currentThread()));
            assertTrue(session.isCloseableBy(Thread.currentThread()));
            segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 42);
            assertEquals((byte) 42, segment.get(ValueLayout.JAVA_BYTE, 0));
            session.acquire0();
            try {
                assertThrows(IllegalStateException.class, arena::close);
                assertTrue(arena.scope().isAlive());
            } finally {
                session.release0();
            }
        } finally {
            try {
                arena.close();
            } finally {
                lock.writeLock().unlock();
            }
        }
        assertFalse(arena.scope().isAlive());
    }
}
