/*
 * Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.annotation.ForceInline;

/**
 * A pool that associates a stack with a thread. A thread-local stack can be retrieved
 * using {@link StackPool#get()}.
 */
public final class StackPool {

    private final long blockSize;
    private final int capacity;

    private StackPool(long blockSize, int capacity) {
        this.blockSize = blockSize;
        this.capacity = capacity;
    }

    TerminatingThreadLocal<Stack> STACK_POOL = new TerminatingThreadLocal<>() {
        @Override
        protected Stack initialValue() {
            return new Stack(blockSize, capacity);
        }

        @Override
        protected void threadTerminated(Stack value) {
            value.close();
        }
    };

    /**
     * {@return new arena associated with the stack pool in the current thread}
     */
    @ForceInline
    public Arena get() {
        Stack stack = STACK_POOL.get();
        if (Thread.currentThread().isVirtual()) {
            Continuation.pin();
        }
        return stack.push();
    }

    /**
     * {@return a new stack arena, with unbounded capacity and block size set to {@link #DEFAULT_BLOCK_SIZE}}
     * Equivalent to the following code:
     * {@snippet lang=java :
     * StackArena.newStack(DEFAULT_BLOCK_SIZE);
     * }
     */
    public static StackPool of() {
        return of(DEFAULT_BLOCK_SIZE);
    }

    /**
     * {@return a new stack arena, with unbounded capacity and given block size}
     * @param blockSize the stack block size
     * @throws IllegalArgumentException if {@code blockSize <= 0}
     */
    public static StackPool of(long blockSize) {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Invalid block size: " + blockSize);
        }
        return new StackPool(blockSize, -1);
    }

    /**
     * {@return a new stack arena, with the given block size and capacity}
     * @param blockSize the stack block size
     * @param capacity the stack capacity
     * @throws IllegalArgumentException if {@code blockSize <= 0}
     * @throws IllegalArgumentException if {@code capacity <= 0}
     */
    public static StackPool of(long blockSize, int capacity) {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Invalid block size: " + blockSize);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Invalid capacity: " + capacity);
        }
        return new StackPool(blockSize, capacity);
    }

    /**
     * The default stack block size
     */
    public static final long DEFAULT_BLOCK_SIZE = 4 * 1024;
}
