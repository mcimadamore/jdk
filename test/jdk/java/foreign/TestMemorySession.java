/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @modules java.base/jdk.internal.ref java.base/jdk.internal.foreign
 * @run testng/othervm TestMemorySession
 */

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.ref.CleanerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class TestMemorySession {

    final static int N_THREADS = 100;

    @Test
    public void testConfined() {
        AtomicInteger acc = new AtomicInteger();
        MemorySessionImpl session = MemorySessionImpl.createConfined(Thread.currentThread());
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            addCloseAction(session, () -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        session.close();
        assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
    }

    @Test(dataProvider = "sharedSessions")
    public void testSharedSingleThread(SessionSupplier sessionSupplier) {
        AtomicInteger acc = new AtomicInteger();
        MemorySessionImpl session = sessionSupplier.get();
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            addCloseAction(session, () -> acc.addAndGet(delta));
        }
        assertEquals(acc.get(), 0);

        if (!SessionSupplier.isImplicit(session)) {
            SessionSupplier.close(session);
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            session = null;
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test(dataProvider = "sharedSessions")
    public void testSharedMultiThread(SessionSupplier sessionSupplier) {
        AtomicInteger acc = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        MemorySessionImpl session = sessionSupplier.get();
        AtomicReference<MemorySessionImpl> sessionRef = new AtomicReference<>(session);
        for (int i = 0 ; i < N_THREADS ; i++) {
            int delta = i;
            Thread thread = new Thread(() -> {
                try {
                    addCloseAction(sessionRef.get(), () -> {
                        acc.addAndGet(delta);
                    });
                } catch (IllegalStateException ex) {
                    // already closed - we need to call cleanup manually
                    acc.addAndGet(delta);
                }
            });
            threads.add(thread);
        }
        assertEquals(acc.get(), 0);
        threads.forEach(Thread::start);

        // if no cleaner, close - not all segments might have been added to the session!
        // if cleaner, don't unset the session - after all, the session is kept alive by threads
        if (!SessionSupplier.isImplicit(session)) {
            while (true) {
                try {
                    SessionSupplier.close(session);
                    break;
                } catch (IllegalStateException ise) {
                    // session is acquired (by add) - wait some more
                }
            }
        }

        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException ex) {
                fail();
            }
        });

        if (!SessionSupplier.isImplicit(session)) {
            assertEquals(acc.get(), IntStream.range(0, N_THREADS).sum());
        } else {
            session = null;
            sessionRef.set(null);
            int expected = IntStream.range(0, N_THREADS).sum();
            while (acc.get() != expected) {
                kickGC();
            }
        }
    }

    @Test
    public void testLockSingleThread() {
        MemorySessionImpl session = MemorySessionImpl.createConfined(Thread.currentThread());
        List<MemorySessionImpl> handles = new ArrayList<>();
        for (int i = 0 ; i < N_THREADS ; i++) {
            MemorySessionImpl handle = MemorySessionImpl.createConfined(Thread.currentThread());
            keepAlive(handle, session);
            handles.add(handle);
        }

        while (true) {
            try {
                session.close();
                assertEquals(handles.size(), 0);
                break;
            } catch (IllegalStateException ex) {
                assertTrue(handles.size() > 0);
                MemorySessionImpl handle = handles.remove(0);
                handle.close();
            }
        }
    }

    @Test
    public void testLockSharedMultiThread() {
        MemorySessionImpl session = MemorySessionImpl.createShared();
        AtomicInteger lockCount = new AtomicInteger();
        for (int i = 0 ; i < N_THREADS ; i++) {
            new Thread(() -> {
                try (MemorySessionImpl handle = MemorySessionImpl.createConfined(Thread.currentThread())) {
                    keepAlive(handle, session);
                    lockCount.incrementAndGet();
                    waitSomeTime();
                    lockCount.decrementAndGet();
                } catch (IllegalStateException ex) {
                    // might be already closed - do nothing
                }
            }).start();
        }

        while (true) {
            try {
                session.close();
                assertEquals(lockCount.get(), 0);
                break;
            } catch (IllegalStateException ex) {
                waitSomeTime();
            }
        }
    }

    @Test
    public void testCloseEmptyConfinedSession() {
        MemorySessionImpl.createConfined(Thread.currentThread()).close();
    }

    @Test
    public void testCloseEmptySharedSession() {
        MemorySessionImpl.createShared().close();
    }

    @Test
    public void testCloseConfinedLock() {
        MemorySessionImpl session = MemorySessionImpl.createConfined(Thread.currentThread());
        MemorySessionImpl handle = MemorySessionImpl.createConfined(Thread.currentThread());
        keepAlive(handle, session);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                handle.close();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        t.start();
        try {
            t.join();
            assertNotNull(failure.get());
            assertEquals(failure.get().getClass(), WrongThreadException.class);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    @Test(dataProvider = "allSessions")
    public void testSessionAcquires(SessionSupplier sessionSupplier) {
        MemorySessionImpl session = sessionSupplier.get();
        acquireRecursive(session, 5);
        if (!SessionSupplier.isImplicit(session))
            SessionSupplier.close(session);
    }

    private void acquireRecursive(MemorySessionImpl session, int acquireCount) {
        try (MemorySessionImpl innerSession = MemorySessionImpl.createConfined(Thread.currentThread())) {
            keepAlive(innerSession, session);
            if (acquireCount > 0) {
                // recursive acquire
                acquireRecursive(session, acquireCount - 1);
            }
            if (!SessionSupplier.isImplicit(session)) {
                assertThrows(IllegalStateException.class, () -> SessionSupplier.close(session));
            }
        }
    }

    @Test
    public void testConfinedSessionWithImplicitDependency() {
        MemorySessionImpl root = MemorySessionImpl.createConfined(Thread.currentThread());
        // Create many implicit sessions which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            keepAlive(MemorySessionImpl.createImplicit(Cleaner.create()), root);
        }
        // Now let's keep trying to close 'root' until we succeed. This is trickier than it seems: cleanup action
        // might be called from another thread (the Cleaner thread), so that the confined session lock count is updated racily.
        // If that happens, the loop below never terminates.
        while (true) {
            try {
                root.close();
                break; // success!
            } catch (IllegalStateException ex) {
                kickGC();
                for (int i = 0 ; i < N_THREADS ; i++) {  // add more races from current thread
                    try (MemorySessionImpl session = MemorySessionImpl.createConfined(Thread.currentThread())) {
                        keepAlive(session, root);
                        // dummy
                    }
                }
                // try again
            }
        }
    }

    @Test
    public void testConfinedSessionWithSharedDependency() {
        MemorySessionImpl root = MemorySessionImpl.createConfined(Thread.currentThread());
        List<Thread> threads = new ArrayList<>();
        // Create many implicit sessions which depend on 'root', and let them become unreachable.
        for (int i = 0; i < N_THREADS; i++) {
            MemorySessionImpl session = MemorySessionImpl.createShared(); // create session inside same thread!
            keepAlive(session, root);
            Thread t = new Thread(session::close); // close from another thread!
            threads.add(t);
            t.start();
        }
        for (int i = 0 ; i < N_THREADS ; i++) { // add more races from current thread
            try (MemorySessionImpl session = MemorySessionImpl.createConfined(Thread.currentThread())) {
                keepAlive(session, root);
                // dummy
            }
        }
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException ex) {
                // ok
            }
        });
        // Now let's close 'root'. This is trickier than it seems: releases of the confined session happen in different
        // threads, so that the confined session lock count is updated racily. If that happens, the following close will blow up.
        root.close();
    }

    private void waitSomeTime() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException ex) {
            // ignore
        }
    }

    private void kickGC() {
        for (int i = 0 ; i < 100 ; i++) {
            byte[] b = new byte[100];
            System.gc();
            Thread.onSpinWait();
        }
    }

    @DataProvider
    static Object[][] drops() {
        return new Object[][] {
                { (Supplier<MemorySessionImpl>) () -> MemorySessionImpl.createConfined(Thread.currentThread()) },
                { (Supplier<MemorySessionImpl>) MemorySessionImpl::createShared },
        };
    }

    private void keepAlive(MemorySessionImpl child, MemorySessionImpl parent) {
        MemorySessionImpl parentImpl = (MemorySessionImpl) parent;
        parentImpl.acquire0();
        addCloseAction(child, parentImpl::release0);
    }

    private void addCloseAction(MemorySessionImpl session, Runnable action) {
        MemorySessionImpl sessionImpl = (MemorySessionImpl) session;
        sessionImpl.addCloseAction(action);
    }

    interface SessionSupplier extends Supplier<MemorySessionImpl> {

        static void close(MemorySessionImpl session) {
            ((MemorySessionImpl)session).close();
        }

        static boolean isImplicit(MemorySessionImpl session) {
            return !((MemorySessionImpl)session).isCloseable();
        }

        static SessionSupplier ofImplicit() {
            return () -> MemorySessionImpl.createImplicit(Cleaner.create());
        }

        static SessionSupplier ofMemorySessionImpl(Supplier<MemorySessionImpl> MemorySessionImplSupplier) {
            return () -> MemorySessionImplSupplier.get();
        }
    }

    @DataProvider(name = "sharedSessions")
    static Object[][] sharedSessions() {
        return new Object[][] {
                { SessionSupplier.ofMemorySessionImpl(MemorySessionImpl::createShared) },
                { SessionSupplier.ofImplicit() },
        };
    }

    @DataProvider(name = "allSessions")
    static Object[][] allSessions() {
        return new Object[][] {
                { SessionSupplier.ofMemorySessionImpl(() -> MemorySessionImpl.createConfined(Thread.currentThread())) },
                { SessionSupplier.ofMemorySessionImpl(MemorySessionImpl::createShared) },
                { SessionSupplier.ofImplicit() },
        };
    }
}
