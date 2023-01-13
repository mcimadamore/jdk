/*
 *  Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.foreign;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.ref.CleanerFactory;

/**
 * A native allocator is used to allocate native segments.
 * <p>
 * A native allocator has a <em>lifetime</em> (see {@link #isAlive()}) which determines the temporal
 * bounds of the memory segments allocated by it. Moreover, a native allocator also determines whether access to
 * memory segments allocated by it should be {@linkplain #isAccessibleBy(Thread) restricted} to specific threads.
 * <p>
 * The simplest native allocator is the {@linkplain Arena#global() global allocator}. The global allocator
 * features an <em>unbounded lifetime</em>. As such, native segments allocated with the global allocator are always
 * accessible and their backing regions of memory are never deallocated. Moreover, memory segments allocated with the
 * global allocator can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} from any thread.
 * {@snippet lang = java:
 * MemorySegment segment = Arena.global().allocate(100);
 * ...
 * // segment is never deallocated!
 *}
 * <p>
 * Alternatively, clients can obtain an {@linkplain Arena#auto() automatic allocator}, that is an allocator
 * which features a <em>bounded lifetime</em> that is managed, automatically, by the garbage collector. As such, the regions
 * of memory backing memory segments allocated with the automatic allocator are deallocated at some unspecified time
 * <em>after</em> the automatic allocator (and all the segments allocated by it) become
 * <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>, as shown below:
 *
 * {@snippet lang = java:
 * MemorySegment segment = Arena.auto().allocate(100);
 * ...
 * segment = null; // the segment region becomes available for deallocation after this point
 *}
 * Memory segments allocated with the automatic allocator can also be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} from any thread.
 * <p>
 * Finally, clients can use an {@linkplain ScopedArena arena}. An arena features a <em>bounded lifetime</em> which
 * starts with the arena, and ends when the arena is {@linkplain ScopedArena#close() closed}. As a result,
 * the regions of memory backing memory segments allocated with an arena are deallocated when the arena is closed.
 * When this happens, all the segments allocated with the arena become not {@linkplain MemorySegment#isAlive() alive},
 * and subsequent access operations on these segments will fail {@link IllegalStateException}.
 *
 * {@snippet lang = java:
 * MemorySegment segment = null;
 * try (ScopedArena arena = ScopedArena.openConfined()) {
 *     segment = arena.allocate(100);
 *     ...
 * } // segment region deallocated here
 * segment.get(ValueLayout.JAVA_BYTE, 0); // throws IllegalStateException
 *}
 *
 * Which threads can {@link MemorySegment#isAccessibleBy(Thread) access} memory segments allocated with an arena depends
 * on the arena kind. For instance, segments allocated with a {@linkplain ScopedArena#openConfined() confined arena}
 * can only be accessed by the thread that created the arena. Conversely, segments allocated with a
 * {@linkplain ScopedArena#openConfined() shared arena} can be accessed by any thread.
 *
 * @implSpec
 * Implementations of this interface are thread-safe.
 *
 * @see ScopedArena
 * @see MemorySegment
 *
 * @since 20
 */
@PreviewFeature(feature =PreviewFeature.Feature.FOREIGN)
public sealed interface Arena extends SegmentAllocator permits ScopedArena, MemorySessionImpl {

    /**
     * Returns {@code true}, if this allocator is alive. Segments allocated by this allocator can only
     * be accessed if this allocator is alive.
     *
     * @see MemorySegment#isAlive()
     * @return {@code true}, if this allocator is alive.
     */
    boolean isAlive();

    /**
     * Returns {@code true}, if the provided thread can access this allocator. Access to segments allocated by this allocator
     * is restricted in a similar fashion. That is, if an allocator can only be accessed by a single thread {@code T},
     * then all the segments allocated by that allocator can only be accessed by that same thread {@code T}.
     *
     * @see MemorySegment#isAccessibleBy(Thread)
     * @param thread the thread to be tested.
     * @return {@code true}, if the provided thread can access this allocator.
     */
    boolean isAccessibleBy(Thread thread);

    /**
     * Returns a native memory segment with the given size (in bytes) and alignment constraint (in bytes).
     * <p>
     * The temporal bounds of the returned segments are determined by this allocator's lifetime. That is,
     * the returned segment can only be accessed as long as this allocator is {@linkplain #isAlive() alive}.
     * <p>
     * The returned segment's {@link MemorySegment#address() address} is the starting address of the
     * allocated off-heap memory region backing the segment, and the address is
     * aligned according the provided alignment constraint.
     *
     * @implSpec
     * Implementations of this method must return a native segment featuring the requested size,
     * and that is compatible with the provided alignment constraint. Furthermore, for any two segments
     * {@code S1, S2} returned by this method, the following invariant must hold:
     *
     * {@snippet lang = java:
     * S1.overlappingSlice(S2).isEmpty() == true
     *}
     *
     * @param byteSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param byteAlignment the alignment constraint (in bytes) of the off-heap region of memory backing the native memory segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}, {@code alignmentBytes <= 0}, or if {@code alignmentBytes}
     * is not a power of 2.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this allocator is no longer {@linkplain #isAlive() alive}.
     */
    @Override
    MemorySegment allocate(long byteSize, long byteAlignment);

    /**
     * Creates a native segment with the given address, and cleanup action.
     * <p>
     * The temporal bounds of the returned segments are determined by this allocator's lifetime. That is,
     * the returned segment can only be accessed as long as this allocator is {@linkplain #isAlive() alive}.
     * <p>
     * The returned segment's {@link MemorySegment#address() address} is the provided address, which presumably
     * denotes the starting address of some externally allocated off-heap memory region. The size of the returned
     * segment is 0. That is, the returned segment is a <a href="MemorySegment.html#wrapping-addresses">zero-length memory segment</a>.
     * The returned segment can be {@link MemorySegment#asUnboundedSlice() resized unsafely}.
     * <p>
     * The returned segment is not read-only (see {@link MemorySegment#isReadOnly()}).
     * <p>
     * This method can be useful when interacting with custom memory sources (e.g. custom allocators),
     * where an address to some underlying region of memory is typically obtained from foreign code
     * (often as a plain {@code long} value).
     * <p>
     * The provided cleanup action (if any) will be invoked when the returned segment becomes not {@linkplain MemorySegment#isAlive() alive}.
     *
     * @param address the returned segment's address.
     * @param cleanupAction the custom cleanup action to be associated to the returned segment (can be null).
     * @return a native segment with the given address and cleanup action (if any).
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code isAccessibleBy(T) == false}.
     * @throws IllegalStateException if this allocator is no longer {@linkplain #isAlive() alive}.
     */
    MemorySegment wrap(long address, Runnable cleanupAction);

    /**
     * Obtains an automatic allocator. The lifetime of an automatic allocator is managed, implicitly,
     * by the garbage collector. That is, an automatic allocator remains {@linkplain #isAlive() alive} as long
     * as it (or any of the segments allocated by it) is kept reachable. Moreover, an automatic allocator
     * can be {@linkplain #isAccessibleBy(Thread)} accessed by any thread.
     *
     * @return a new automatic allocator.
     */
    static Arena auto() {
        return MemorySessionImpl.createImplicit(CleanerFactory.cleaner());
    }

    /**
     * Obtains the global allocator. The global allocator is always {@linkplain #isAlive() alive}
     * and can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread.
     *
     * @return the global allocator.
     */
    static Arena global() {
        return MemorySessionImpl.GLOBAL;
    }
}
