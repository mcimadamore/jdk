/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

public class Stack {

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

    @SuppressWarnings("restricted")
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
        public MemorySegment.Scope scope() {
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
            // invariant: there can be only one arena such that: (a) its scope is not yet closed, and (b) its start offset
            // is equal to the pending offset in the stack. Ideally, we'd just save a pointer to the top arena in the stack,
            // but doing so defeats escape analysis optimizations.
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
        return new Stack(DEFAULT_BLOCK_SIZE, -1);
    }
}
