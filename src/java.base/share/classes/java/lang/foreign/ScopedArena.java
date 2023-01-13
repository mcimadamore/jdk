/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * A scoped arena controls the lifecycle of memory segments, providing both flexible allocation and timely deallocation.
 * <p>
 * A scoped arena is used to allocate native segments. When the arena is {@linkplain #close() closed},
 * all the segments allocated by the scoped arena are invalidated, safely and atomically, their backing memory regions are
 * deallocated (where applicable) and can no longer be accessed:
 *
 * {@snippet lang = java:
 * try (ScopedArena arena = Arena.openConfined()) {
 *     MemorySegment segment = arena.allocate(100);
 *     ...
 * } // memory released here
 *}
 *
 * <h2 id = "thread-confinement">Safety and thread-confinement</h2>
 *
 * Scoped arenas provide strong temporal safety guarantees: a memory segment allocated by a scoped arena cannot be accessed
 * <em>after</em> the scoped arena has been closed. The cost of providing this guarantee varies based on the
 * number of threads that have access to the memory segments allocated by the scoped arena. For instance, if a scoped arena
 * is always created and closed by one thread, and the memory segments associated with the scoped arena are always
 * accessed by that same thread, then ensuring correctness is trivial.
 * <p>
 * Conversely, if a scoped arena allocates segments that can be accessed by multiple threads, or if the scoped arena can be closed
 * by a thread other than the accessing thread, then ensuring correctness is much more complex. For example, a segment
 * allocated with the scoped arena might be accessed <em>while</em> another thread attempts, concurrently, to close the scoped arena.
 * To provide the strong temporal safety guarantee without forcing every client, even simple ones, to incur a performance
 * impact, scoped arenas are divided into <em>thread-confined</em> scoped arenas, and <em>shared</em> scoped arenas.
 * <p>
 * Confined scoped arenas, support strong thread-confinement guarantees. Upon creation, they are assigned an
 * {@linkplain #isCloseableBy(Thread) owner thread}, typically the thread which initiated the creation operation.
 * The segments created by a scoped confined arena can only be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed}
 * by the owner thread. Moreover, any attempt to close the scoped confined arena from a thread other than the owner thread will
 * fail with {@link WrongThreadException}.
 * <p>
 * Shared scoped arenas, on the other hand, have no owner thread. The segments created by a shared scoped arena
 * can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread. This might be useful when
 * multiple threads need to access the same memory segment concurrently (e.g. in the case of parallel processing).
 * Moreover, a shared scoped arena {@linkplain #isCloseableBy(Thread) can be closed} by any thread.
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public non-sealed class ScopedArena implements Arena, AutoCloseable {

    private MemorySessionImpl sessionImpl;

    /**
     * Wraps a new arena around an existing one.
     * @param arena arena.
     */
    protected ScopedArena(ScopedArena arena) {
        this(arena.sessionImpl);
    }

    ScopedArena(MemorySessionImpl sessionImpl) {
        this.sessionImpl = sessionImpl;
    }

    /**
     * Closes this arena. If this method completes normally, the segments associated with this arena
     * are no longer {@linkplain MemorySegment#isAlive() alive}, and can no longer be accessed.
     * Furthermore, any off-heap region of memory backing the segments associated with this arena are also released.
     *
     * @apiNote This operation is not idempotent; that is, closing an already closed arena <em>always</em> results in an
     * exception being thrown. This reflects a deliberate design choice: failure to close an arena might reveal a bug
     * in the underlying application logic.
     *
     * @see MemorySegment#isAlive()
     *
     * @throws IllegalStateException if the arena has already been closed.
     * @throws IllegalStateException if one or more segments associated with this arena is being
     * {@linkplain MemorySegment#whileAlive(Runnable) kept alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code isCloseableBy(T) == false}.
     */
    @Override
    public void close() {
        sessionImpl.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isAlive() {
        return sessionImpl.isAlive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CallerSensitive
    public final MemorySegment wrap(long address, long size, Runnable cleanupAction) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), ScopedArena.class, "wrap");
        return sessionImpl.wrap(address, size, cleanupAction);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return sessionImpl.allocate(byteSize, byteAlignment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isAccessibleBy(Thread thread) {
        return sessionImpl.isAccessibleBy(thread);
    }

    /**
     * {@return {@code true} if the provided thread can close this arena}
     * @param thread the thread to be tested.
     */
    public final boolean isCloseableBy(Thread thread) {
        return sessionImpl.isCloseableBy(thread);
    }

}
