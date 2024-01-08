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

package jdk.internal.foreign;

import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StackArena;

/**
 * A helper class to manage slice-based allocators.
 * @see StackArena
 */
public class Block {

    public static final long DEFAULT_BLOCK_SIZE = 4 * 1024;

    private final long rem, blockSize;
    private final MemorySegment segment;
    private final Arena arena;
    private Block next;

    public Block(long size, long rem, Arena arena) {
        this.blockSize = size;
        if (rem - size < 0) {
            this.rem = 0;
            size = rem;
        } else {
            this.rem = rem - size;
        }
        this.arena = arena;
        this.segment = arena.allocate(size, 1);
    }

    public Cursor start() {
        return new Cursor(this, 0, 0);
    }

    @ForceInline
    private Block nextOrAllocate() {
        if (next == null) {
            if (rem == 0) {
                throw new OutOfMemoryError();
            }
            next = new Block(blockSize, rem, arena);
        }
        return next;
    }

    public static class Cursor {
        Block block;
        long offset;
        long size;

        Cursor(Block block, long offset, long size) {
            this.block = block;
            this.offset = offset;
            this.size = size;
        }

        private void trySlice(long bytesSize, long bytesAlignment) {
            long min = block.segment.address();
            long start = Utils.alignUp(min + offset + size, bytesAlignment) - min;
            if (block.segment.byteSize() - start < bytesSize) {
                offset = -1;
            } else {
                offset = start;
                size = bytesSize;
            }
        }

        public MemorySegment nextSlice(long bytesSize, long bytesAlignment, Arena arena) {
            // try to slice from current segment first...
            trySlice(bytesSize, bytesAlignment);
            if (offset != -1) {
                return MemorySegment.ofAddress(block.segment.address() + offset).reinterpret(bytesSize, arena, null);
            } else {
                long maxPossibleAllocationSize = bytesSize + bytesAlignment - 1;
                if (maxPossibleAllocationSize > block.blockSize) {
                    // too big
                    throw new IllegalArgumentException("Allocation size > block size");
                } else {
                    // allocate a new segment and slice from there
                    block = block.nextOrAllocate();
                    offset = 0;
                    size = 0;
                    return nextSlice(bytesSize, bytesAlignment, arena);
                }
            }
        }

        public Cursor dup() {
            return new Cursor(block, offset, size);
        }
    }
}
