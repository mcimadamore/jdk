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
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import static java.lang.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 3, jvmArgsAppend = {"--enable-preview", "--enable-native-access=ALL-UNNAMED"})
public class LoopOverNonConstantScoped extends JavaLayouts {
    static final int ELEM_SIZE = 1_000_000;
    static final ScopedValue<Boolean> BINDING = ScopedValue.newInstance();

    @Param({"SHARED", "SCOPED"})
    public String kind;

    final ScopedValue<Boolean> dynamicBinding = BINDING;

    Arena backingArena;
    MemorySegment backing;
    MemorySegment segment;

    @Setup
    public void setup() {
        backingArena = Arena.ofShared();
        backing = backingArena.allocate((long) ELEM_SIZE * Integer.BYTES, Integer.BYTES);
        for (int i = 0; i < ELEM_SIZE; i++) backing.setAtIndex(JAVA_INT, i, i);
    }

    @TearDown
    public void tearDown() {
        backingArena.close();
    }

    // A new captured binding is needed per invocation. The shared control uses
    // identical binding scaffolding and the same backing memory. Scoped creation,
    // reinterpretation and close are included, but not native allocation/free.
    int withArena(ScopedValue.CallableOp<Integer, Exception> action) throws Exception {
        return ScopedValue.where(BINDING, true).call(() -> {
            if (kind.equals("SCOPED")) {
                try (Arena scoped = Arena.ofScoped(BINDING)) {
                    segment = backing.reinterpret(backing.byteSize(), scoped, null);
                    return action.call();
                }
            } else {
                segment = backing;
                return action.call();
            }
        });
    }

    int loop() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += (int) VH_INT.get(segment, (long) i);
        }
        return sum;
    }

    int loopAtIndex() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            sum += segment.getAtIndex(JAVA_INT, i);
        }
        return sum;
    }

    @Benchmark
    public int owner_loop() throws Exception {
        return withArena(this::loop);
    }

    @Benchmark
    public int owner_loop_at_index() throws Exception {
        return withArena(this::loopAtIndex);
    }

    // Diagnostic controls: run with kind=SHARED to isolate the binding check.
    @Benchmark
    public int owner_loop_check_bound_constant() throws Exception {
        return withArena(() -> {
            int sum = 0;
            for (int i = 0; i < ELEM_SIZE; i++) {
                if (!BINDING.isBound()) throw new AssertionError();
                sum += (int) VH_INT.get(segment, (long) i);
            }
            return sum;
        });
    }

    @Benchmark
    public int owner_loop_check_bound_dynamic() throws Exception {
        return withArena(() -> {
            int sum = 0;
            for (int i = 0; i < ELEM_SIZE; i++) {
                if (!dynamicBinding.isBound()) throw new AssertionError();
                sum += (int) VH_INT.get(segment, (long) i);
            }
            return sum;
        });
    }

    @Benchmark
    public int owner_loop_check_get_dynamic() throws Exception {
        return withArena(() -> {
            int sum = 0;
            for (int i = 0; i < ELEM_SIZE; i++) {
                dynamicBinding.get();
                sum += (int) VH_INT.get(segment, (long) i);
            }
            return sum;
        });
    }

    @Benchmark
    public int nested_loop() throws Exception {
        return withArena(() -> {
            try (var scope = StructuredTaskScope.open()) {
                var task = scope.fork(this::loop);
                scope.join();
                return task.get();
            }
        });
    }

    @Benchmark
    public int nested_loop_at_index() throws Exception {
        return withArena(() -> {
            try (var scope = StructuredTaskScope.open()) {
                var task = scope.fork(this::loopAtIndex);
                scope.join();
                return task.get();
            }
        });
    }
}
