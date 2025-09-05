/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign;

import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * A shared session, which can be shared across multiple threads. Closing a shared session has to ensure that
 * (i) only one thread can successfully close a session (e.g. in a close vs. close race) and that
 * (ii) no other thread is accessing the memory associated with this session while the segment is being
 * closed. To ensure the former condition, a CAS is performed on the liveness bit. Ensuring the latter
 * is trickier, and require a complex synchronization protocol (see {@link jdk.internal.misc.ScopedMemoryAccess}).
 * Since it is the responsibility of the closing thread to make sure that no concurrent access is possible,
 * checking the liveness bit upon access can be performed in plain mode, as in the confined case.
 */
sealed class SharedSession extends MemorySessionImpl permits ImplicitSession {

    private static final boolean DEFER_CLEANUP = Boolean.parseBoolean(
            System.getProperty("jdk.internal.foreign.SharedSession.DEFER_CLEANUP", "true"));

    private static final boolean USE_VIRTUAL_THREAD_CLEANUP = Boolean.parseBoolean(
            System.getProperty("jdk.internal.foreign.SharedSession.USE_VIRTUAL_THREAD_CLEANUP", "true"));

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    private static final int CLOSED_ACQUIRE_COUNT = -1;

    SharedSession() {
        super(null, new SharedResourceList());
    }

    @Override
    @ForceInline
    public void acquire0() {
        int value;
        do {
            value = (int) ACQUIRE_COUNT.getVolatile(this);
            if (value < 0) {
                //segment is not open!
                throw sharedSessionAlreadyClosed();
            } else if (value == MAX_FORKS) {
                //overflow
                throw tooManyAcquires();
            }
        } while (!ACQUIRE_COUNT.compareAndSet(this, value, value + 1));
    }

    @Override
    @ForceInline
    public void release0() {
        int value;
        do {
            value = (int) ACQUIRE_COUNT.getVolatile(this);
            if (value <= 0) {
                //cannot get here - we can't close segment twice
                throw sharedSessionAlreadyClosed();
            }
        } while (!ACQUIRE_COUNT.compareAndSet(this, value, value - 1));
    }

    void justClose() {
        int acquireCount = (int) ACQUIRE_COUNT.compareAndExchange(this, 0, CLOSED_ACQUIRE_COUNT);
        if (acquireCount < 0) {
            throw sharedSessionAlreadyClosed();
        } else if (acquireCount > 0) {
            throw alreadyAcquired(acquireCount);
        }

        STATE.setVolatile(this, CLOSED);
        if (DEFER_CLEANUP) {
            SharedSessionCleaner.INSTANCE.register(this);
        } else {
            closeSession(this);
        }
    }

    static void closeSessions(SharedSession[] sessions, int count) {
        SCOPED_MEMORY_ACCESS.closeScope(sessions, count, ALREADY_CLOSED);
        for (int i = 0 ; i < count ; i++) {
            sessions[i].resourceList.cleanup();
            sessions[i] = null;
        }
    }

    static void closeSession(SharedSession session) {
        closeSessions(new SharedSession[] { session }, 1);
    }

    private IllegalStateException sharedSessionAlreadyClosed() {
        // To avoid the situation where a scope fails to be acquired or closed but still reports as
        // alive afterward, we wait for the state to change before throwing the exception
        while ((int) STATE.getVolatile(this) == OPEN) {
            Thread.onSpinWait();
        }
        return alreadyClosed();
    }

    @Override
    public void close() {
        justClose();
    }

    /**
     * A shared resource list; this implementation has to handle add vs. add races, as well as add vs. cleanup races.
     */
    static class SharedResourceList extends ResourceList {

        static final VarHandle FST = MhUtil.findVarHandle(
                MethodHandles.lookup(), ResourceList.class, "fst", ResourceCleanup.class);

        @Override
        void add(ResourceCleanup cleanup) {
            while (true) {
                ResourceCleanup prev = (ResourceCleanup) FST.getVolatile(this);
                if (prev == ResourceCleanup.CLOSED_LIST) {
                    // too late
                    throw alreadyClosed();
                }
                cleanup.next = prev;
                if (FST.compareAndSet(this, prev, cleanup)) {
                    return; //victory
                }
                // keep trying
            }
        }

        void cleanup() {
            // At this point we are only interested about add vs. close races - not close vs. close
            // (because MemorySessionImpl::justClose ensured that this thread won the race to close the session).
            // So, the only "bad" thing that could happen is that some other thread adds to this list
            // while we're closing it.
            if (FST.getAcquire(this) != ResourceCleanup.CLOSED_LIST) {
                //ok now we're really closing down
                ResourceCleanup prev = null;
                while (true) {
                    prev = (ResourceCleanup) FST.getVolatile(this);
                    // no need to check for DUMMY, since only one thread can get here!
                    if (FST.compareAndSet(this, prev, ResourceCleanup.CLOSED_LIST)) {
                        break;
                    }
                }
                cleanup(prev);
            } else {
                throw alreadyClosed();
            }
        }
    }

    static class SharedSessionCleaner implements Runnable {

        static final int BATCH_SIZE = 1000;

        final BlockingDeque<SharedSession> toClose = new LinkedBlockingDeque<>();

        void register(SharedSession sharedSession) {
            if (toClose.size() > BATCH_SIZE * 2) {
                // do this right now
                closeSession(sharedSession);
            } else {
                // enque for deferred cleaning
                toClose.add(sharedSession);
            }
        }

        @Override
        public void run() {
            try {
                SharedSession[] sessions = new SharedSession[BATCH_SIZE];
                while (true) {
                    int count = 0;
                    while (count < BATCH_SIZE && toClose.peek() != null) {
                        sessions[count++] = toClose.take();
                    }
                    if (count > 0) {
                        // handshake a batch of sessions
                        closeSessions(sessions, count);
                    } else {
                        // queue is empty, wait for more
                        SharedSession session = toClose.take();
                        // ok, now we're ready to go, but we have to put that back
                        toClose.offer(session);
                    }
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

        static final SharedSessionCleaner INSTANCE = new SharedSessionCleaner();

        static {
            if (USE_VIRTUAL_THREAD_CLEANUP) {
                Thread.ofVirtual().name("SharedSessionCleaner").start(INSTANCE);
            } else {
                Thread.ofPlatform().name("SharedSessionCleaner").start(INSTANCE);
            }
        }
    }
}
