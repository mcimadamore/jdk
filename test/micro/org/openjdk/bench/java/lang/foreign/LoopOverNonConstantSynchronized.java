/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
public class LoopOverNonConstantSynchronized extends JavaLayouts {
    static final int ELEM_SIZE = 1_000_000;
    @Param({"SHARED", "MONITOR"}) public String kind;
    final Object monitor = new Object();
    Arena arena;
    MemorySegment segment;

    @Setup
    public void setup() {
        synchronized (monitor) {
            arena = kind.equals("MONITOR") ? Arena.ofSynchronized(monitor) : Arena.ofShared();
            segment = arena.allocate((long) ELEM_SIZE * Integer.BYTES, Integer.BYTES);
            for (int i = 0; i < ELEM_SIZE; i++) segment.setAtIndex(JAVA_INT, i, i);
        }
    }

    @TearDown
    public void tearDown() {
        synchronized (monitor) { arena.close(); }
    }

    @Benchmark
    public int segment_loop() {
        synchronized (monitor) {
            int sum = 0;
            for (int i = 0; i < ELEM_SIZE; i++) sum += (int) VH_INT.get(segment, (long) i);
            return sum;
        }
    }

    @Benchmark
    public int opaque_segment_loop() {
        synchronized (monitor) { return opaqueLoop(); }
    }

    // The synchronized caller cannot be seen when compiling the access loop.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int opaqueLoop() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) sum += (int) VH_INT.get(segment, (long) i);
        return sum;
    }
}
