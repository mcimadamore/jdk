/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.foreign.Utils;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment.Scope;
import java.util.ArrayList;
import java.util.List;

/**
 * A thread-confined stack that can be reused by multiple clients to speed up allocation.
 * <p>
 * A stack contains a list of native memory segments {@code SS}, where each segment has size {@code B} - we
 * call this the stack's block size. The maximum number of segments in {@code SS} can be bounded by the
 * stack's <em>capacity</em> (if provided at construction). As the stack responds to allocation requests
 * (via an arena, see below), the contents of {@code SS} will be updated accordingly. A stack (and all the arenas
 * derived by it) can only be used by a single thread, the thread that created the stack.
 * <p>
 * Clients can allocate using the stack by calling its {@link #push()} method. This method returns a new
 * arena, which will be backed by the segments {@code SS} in the stack. An arena is said to be the
 * <em>topmost</em> arena if it is the latest arena to be returned by the {@link #push()} method.
 * Clients cannot interact with an arena obtained from a stack, unless it is the topmost arena.
 * When the topmost arena is {@link #close() closed} the memory allocated within it is returned to the stack, so that
 * it can be efficiently recycled. Closing the topmost arena might cause another, previously obtained arena,
 * to become the new topmost arena. When the stack itself is closed, the memory associated with the stack
 * (e.g. the memory segments in {@code SS}) will be released. A stack can be closed only if there's no
 * open topmost arena associated with it.
 * <p>
 * Let {@code I} be the index of the current segment being used by the topmost arena. The topmost arena responds to
 * allocation requests as follows:
 * <ul>
 *     <li>if the size of the allocation requests is smaller than {@code B}, and {@code SS[I]} has a <em>free</em>
 *     slice {@code S} which fits that allocation request, return that {@code S};
 *     <li>if the size of the allocation requests is smaller than {@code B}, and {@code SS[I]} has no <em>free</em>
 *     slices which fits that allocation request, {@code I} is set to {@code I + 1}. Then, if {@code SS[I] == null},
 *     a new segment {@code S}, with size {@code B}, is allocated and added to {@code SS}. The arena then tries to respond
 *     to the same allocation request again;
 *     <li>if the size of the allocation requests is bigger than {@code B}, an {@link IllegalArgumentException}
 *     is thrown.</li>
 * </ul>
 * The topmost arena might throw an {@link OutOfMemoryError} if, during its use, the total memory allocated by all the
 * open arenas associated with the stack exceeds the system capacity, or whether {@code SS} exceeds the stack's capacity.
 * <p>
 * A stack can be useful when clients want to perform multiple allocation requests while avoiding the
 * cost associated with allocating a new off-heap memory region upon each allocation request:
 *
 * {@snippet lang = java:
 * try (Stack stack = Stack.newStack()) {
 *     ...
 *     for (int i = 0 ; i < 1000 ; i++) {
 *         try (Arena localArena = stack.push()) {
 *             ...
 *             MemorySegment.allocateNative(100, localArena);
 *             ...
 *         } // arena memory recycled
 *     }
 *     ...
 *  } // arena memory released
 *}
 *
 * The above code creates a new stack. It then allocates memory in a loop; at each iteration,
 * a new topmost arena is obtained by calling the {@link #push()} method. When the topmost arena is closed,
 * the allocated memory is returned to the stack and then recycled on the subsequent iteration.
 * When the stack is closed, the off-heap memory associated with the stack is released.
 */
public final class Stack implements AutoCloseable {

    long offset = 0L;
    long pendingStart = CLOSEABLE;
    final long blockSize;
    final int capacity;
    final List<MemorySegment> segments = new ArrayList<>();
    final Arena stackArena = Arena.ofConfined();

    /**
     * The default stack block size
     */
    public static final long DEFAULT_BLOCK_SIZE = 4 * 1024;
    private static final long CLOSEABLE = -1;
    private static final long NO_SLICE = -2;

    Stack(long blockSize, int capacity) {
        this.blockSize = blockSize;
        this.capacity = capacity;
    }

    /**
     * Closes this stack arena. All the segments allocated by this arena can no longer be accessed. If this arena
     * is the outermost stack arena, all the memory resources associated with the arena are released.
     * @throws IllegalStateException if there is an open topmost arena associated with this stack.
     * @throws IllegalStateException if this stack has already been closed.
     * @throws WrongThreadException if this method is called from a thread other than the stack's owner thread
     */
    @Override
    public void close() {
        if (pendingStart == CLOSEABLE) {
            stackArena.close();
        } else {
            throw new IllegalStateException("Cannot close stack while a nested arena is in use");
        }
    }

    /**
     * {@return a new topmost arena, backed by this stack}
     * As a result of this invocation, the previous topmost arena (if any) cannot be closed, used to allocate more segments,
     * or to obtain new nested stack arenas until the returned arena is closed. The returned arena is a confined
     * arena, whose owner thread is the same thread from which this stack has been created.
     * @throws IllegalStateException if this stack has already been closed.
     * @throws WrongThreadException if this method is called from a thread other than the stack's owner thread
     */
    @ForceInline
    public Arena push() {
        MemorySessionImpl.toMemorySession(stackArena).checkValidState();
        ArenaStack child = new ArenaStack(offset, pendingStart);
        pendingStart = offset;
        return child;
    }

    MemorySegment currentSegment() {
        int index = (int)(offset / blockSize);
        if (index == segments.size()) {
            if (index == capacity) {
                throw new OutOfMemoryError();
            }
            MemorySegment segment = stackArena.allocate(blockSize, 1);
            segments.add(segment);
            return segment;
        } else {
            return segments.get(index);
        }
    }

    long currentSegmentOffset() {
        return offset % blockSize;
    }

    private long trySlice(MemorySegment segment, long currentOffset, long bytesSize, long bytesAlignment) {
        long min = segment.address();
        long start = Utils.alignUp(min + currentOffset, bytesAlignment) - min;
        if (segment.byteSize() - start < bytesSize) {
            return NO_SLICE;
        } else {
            return start;
        }
    }

    MemorySegment nextSlice(long bytesSize, long bytesAlignment, Arena arena) {
        MemorySegment segment = currentSegment();
        long currentSegmentOffset = currentSegmentOffset();
        // try to slice from current segment first...
        long startOffset = trySlice(segment, currentSegmentOffset, bytesSize, bytesAlignment);
        if (startOffset != NO_SLICE) {
            offset += (startOffset - currentSegmentOffset) + bytesSize;
            return MemorySegment.ofAddress(segment.address() + startOffset).reinterpret(bytesSize, arena, null);
        } else {
            long maxPossibleAllocationSize = bytesSize + bytesAlignment - 1;
            if (maxPossibleAllocationSize > blockSize) {
                // too big
                throw new IllegalArgumentException("Allocation size > block size");
            } else {
                if (currentSegmentOffset != 0) {
                    // reset offset to the start of next block and allocate from there
                    offset += blockSize - currentSegmentOffset();
                }
                return nextSlice(bytesSize, bytesAlignment, arena);
            }
        }
    }

    class ArenaStack implements Arena {

        final Arena arena = Arena.ofConfined();
        final long startOffset;
        final long prevPendingStart;

        public ArenaStack(long startOffset, long prevPendingStart) {
            this.startOffset = startOffset;
            this.prevPendingStart = prevPendingStart;
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            checkTop();
            Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
            return nextSlice(byteSize, byteAlignment, arena);
        }

        @Override
        public Scope scope() {
            return arena.scope();
        }

        @Override
        public void close() {
            checkTop();
            arena.close();
            offset = startOffset;
            pendingStart = prevPendingStart;
        }

        private void checkTop() {
            MemorySessionImpl.toMemorySession(arena).checkValidState();
            if (startOffset != pendingStart) {
                throw new IllegalStateException("Not top arena!");
            }
        }
    }

    /**
     * {@return a new stack arena, with unbounded capacity and block size set to {@link #DEFAULT_BLOCK_SIZE}}
     * Equivalent to the following code:
     * {@snippet lang=java :
     * StackArena.newStack(DEFAULT_BLOCK_SIZE);
     * }
     */
    public static Stack newStack() {
        return newStack(DEFAULT_BLOCK_SIZE);
    }

    /**
     * {@return a new stack arena, with unbounded capacity and given block size}
     * @param blockSize the stack block size
     * @throws IllegalArgumentException if {@code blockSize <= 0}
     */
    public static Stack newStack(long blockSize) {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Invalid block size: " + blockSize);
        }
        return new Stack(blockSize, -1);
    }

    /**
     * {@return a new stack arena, with the given block size and capacity}
     * @param blockSize the stack block size
     * @param capacity the stack capacity
     * @throws IllegalArgumentException if {@code blockSize <= 0}
     * @throws IllegalArgumentException if {@code capacity <= 0}
     */
    public static Stack newStack(long blockSize, int capacity) {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Invalid block size: " + blockSize);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Invalid capacity: " + capacity);
        }
        return new Stack(blockSize, capacity);
    }
}
