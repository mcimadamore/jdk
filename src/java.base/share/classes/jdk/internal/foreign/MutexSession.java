/*
 *  Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import jdk.internal.access.JavaUtilConcurrentLocksAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;

/** A session whose accesses and close are guarded by a caller-held lock. */
final class MutexSession extends MemorySessionImpl {
    private static final JavaUtilConcurrentLocksAccess LOCK_ACCESS =
            SharedSecrets.getJavaUtilConcurrentLocksAccess();

    static final ScopedMemoryAccess.ScopedAccessError LOCK_NOT_HELD =
            new ScopedMemoryAccess.ScopedAccessError(() ->
                    new WrongThreadException("Current thread does not own the arena lock"));

    MutexSession(ReentrantReadWriteLock lock) {
        super(null, new SharedSession.SharedResourceList(), LOCK_ACCESS.lock(lock));
    }

    @Override
    @ForceInline
    public void acquire0() {
        checkValidState();
        int count;
        do {
            count = (int) ACQUIRE_COUNT.getVolatile(this);
            if (count == MAX_FORKS) {
                throw tooManyAcquires();
            }
        } while (!ACQUIRE_COUNT.compareAndSet(this, count, count + 1));
    }

    @Override
    @ForceInline
    public void release0() {
        // A keep-alive operation may have released the caller's lock before returning.
        ACQUIRE_COUNT.getAndAdd(this, -1);
    }

    @Override
    void justClose() {
        if (!lock.isExclusivelyHeldBy(Thread.currentThread())) throw wrongThread();
        checkValidState();
        int count = (int) ACQUIRE_COUNT.getVolatile(this);
        if (count != 0) {
            throw alreadyAcquired(count);
        }
        // Exclusive ownership excludes concurrent accesses, acquires and closes. Outstanding
        // acquisitions exclude close even when the lock is temporarily released.
        state = CLOSED;
    }
}
