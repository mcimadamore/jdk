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
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.ref.CleanerFactory;

/**
 * A native allocator is used to allocate native segments.
 * <p>
 * A native allocator determines the lifetime of the native segments allocated by it. Moreover,
 * a native allocator also determines whether access to memory segments allocated by it should be
 * <a href="Arena.html#thread-confinement">restricted to specific threads</a>.
 * <p>
 * The simplest native allocator is the {@linkplain NativeAllocator#global() global allocator}. Native segments
 * allocated with the global allocator are always accessible and their backing regions of memory are never deallocated.
 * Moreover, memory segments allocated with the global scope can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed}
 * from any thread.
 * {@snippet lang = java:
 * MemorySegment segment = NativeAllocator.global().allocate(100);
 * ...
 * // segment is never deallocated!
 *}
 * <p>
 * Alternatively, clients can obtain an {@linkplain NativeAllocator#auto() automatic allocator}, that is an allocator
 * whose allocated segments are managed, automatically, by the garbage collector. The regions of memory backing memory
 * segments allocated with the automatic allocator are deallocated at some unspecified time <em>after</em> they become
 * <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>, as shown below:
 *
 * {@snippet lang = java:
 * MemorySegment segment = NativeAllocator.auto().allocate(100);
 * ...
 * segment = null; // the segment region becomes available for deallocation after this point
 *}
 * Memory segments allocated with the automatic allocator can also be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} from any thread.
 * <p>
 * Finally, clients can use an {@linkplain Arena arena}. The regions of memory
 * backing memory segments allocated with an arena are deallocated when the arena is {@linkplain Arena#close() closed}.
 * When this happens, all the segments allocated with the arena become not {@linkplain MemorySegment#isAlive() alive},
 * and subsequent access operations on these segments will fail {@link IllegalStateException}.
 *
 * {@snippet lang = java:
 * MemorySegment segment = null;
 * try (Arena arena = Arena.openConfined()) {
 *     segment = arena.scope().allocate((MemoryLayout)100);
 *     ...
 * } // segment region deallocated here
 * segment.get(ValueLayout.JAVA_BYTE, 0); // throws IllegalStateException
 *}
 *
 * Which threads can {@link MemorySegment#isAccessibleBy(Thread) access} memory segments allocated with an arena scope depends
 * on the arena kind. For instance, segments allocated with a {@linkplain Arena#openConfined() confined arena}
 * can only be accessed by the thread that created the arena. Conversely, segments allocated with a
 * {@linkplain Arena#openConfined() shared arena} can be accessed by any thread.
 *
 * @implSpec
 * Implementations of this interface are thread-safe.
 *
 * @see Arena
 * @see MemorySegment
 *
 * @since 20
 */
@PreviewFeature(feature =PreviewFeature.Feature.FOREIGN)
public interface NativeAllocator extends SegmentAllocator {

    /**
     * Returns a native memory segment with the given size (in bytes) and alignment constraint (in bytes).
     * The returned segment is associated with the arena scope.
     * The segment's {@link MemorySegment#address() address} is the starting address of the
     * allocated off-heap memory region backing the segment, and the address is
     * aligned according the provided alignment constraint.
     *
     * @implSpec
     * The default implementation of this method is equivalent to the following code:
     * {@snippet lang = java:
     * MemorySegment.allocateNative(bytesSize, byteAlignment, this);
     *}
     * More generally implementations of this method must return a native segment featuring the requested size,
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
     */
    @Override
    default MemorySegment allocate(long byteSize, long byteAlignment) {
        return allocate(byteSize, byteAlignment);
    }

    /**
     * Creates a native segment with the given address, and cleanup action.
     * This method can be useful when interacting with custom memory sources (e.g. custom allocators),
     * where an address to some underlying region of memory is typically obtained from foreign code
     * (often as a plain {@code long} value).
     * <p>
     * The returned segment is not read-only (see {@link MemorySegment#isReadOnly()}), and is associated with the
     * provided scope.
     * <p>
     * The provided cleanup action (if any) will be invoked when the returned segment becomes not {@linkplain MemorySegment#isAlive() alive}.
     * <p>
     * Clients should ensure that the address and bounds refer to a valid region of memory that is accessible for reading and,
     * if appropriate, writing; an attempt to access an invalid address from Java code will either return an arbitrary value,
     * have no visible effect, or cause an unspecified exception to be thrown.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     *
     * @param address the returned segment's address.
     * @param cleanupAction the custom cleanup action to be associated to the returned segment (can be null).
     * @return a native segment with the given address, size and scope.
     * @throws IllegalArgumentException if {@code byteSize < 0}.
     * @throws IllegalStateException if {@code scope} is an already closed {@link Arena}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is specified, but does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    MemorySegment wrap(long address, Runnable cleanupAction);

    /**
     * Obtains the automatic allocator. Segments allocated with the automatic allocator are managed, implicitly,
     * by the garbage collector, and can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread.
     *
     * @return the automatic allocator.
     */
    static NativeAllocator auto() {
        class Holder {
            static NativeAllocator AUTO_ALLOCATOR = new NativeAllocator() {
                @Override
                public MemorySegment allocate(long byteSize, long byteAlignment) {
                    return MemorySessionImpl.createImplicit(CleanerFactory.cleaner()).allocate(byteSize, byteAlignment);
                }

                @Override
                public MemorySegment wrap(long address, Runnable cleanupAction) {
                    return MemorySessionImpl.createImplicit(CleanerFactory.cleaner()).wrap(address, cleanupAction);
                }
            };
        }
        return Holder.AUTO_ALLOCATOR;
    }

    /**
     * Obtains the global allocator. Segments allocated are always {@link MemorySegment#isAlive()} and can be
     * {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread.
     *
     * @return the global scope.
     */
    static NativeAllocator global() {
        class Holder {
            static NativeAllocator GLOBAL_ALLOCATOR = new NativeAllocator() {
                @Override
                public MemorySegment allocate(long byteSize, long byteAlignment) {
                    return MemorySessionImpl.GLOBAL.allocate(byteSize, byteAlignment);
                }

                @Override
                public MemorySegment wrap(long address, Runnable cleanupAction) {
                    return MemorySessionImpl.GLOBAL.wrap(address, cleanupAction);
                }
            };
        }
        return Holder.GLOBAL_ALLOCATOR;
    }
}
