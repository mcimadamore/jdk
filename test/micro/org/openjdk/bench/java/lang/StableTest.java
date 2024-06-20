/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang;

import java.lang.foreign.*;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class StableTest {

    static final MethodHandle SUM_HANDLE;
    static final StableValue<MethodHandle> SUM_HANDLE_STABLE_HOLDER;
    static final NonStableHolder SUM_HANDLE_NONSTABLE_HOLDER;

    static class NonStableHolder {
        final MethodHandle methodHandle;

        public NonStableHolder(MethodHandle methodHandle) {
            this.methodHandle = methodHandle;
        }
    }

    static {
        try {
            SUM_HANDLE = MethodHandles.lookup()
                    .findStatic(StableTest.class, "sum", MethodType.methodType(int.class, int.class, int.class));
            SUM_HANDLE_STABLE_HOLDER = StableValue.newInstance(MethodHandle.class);
            SUM_HANDLE_STABLE_HOLDER.trySet(SUM_HANDLE);
            SUM_HANDLE_NONSTABLE_HOLDER = new NonStableHolder(SUM_HANDLE);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static int sum(int i1, int i2) {
        return i1 + i2;
    }

    @Benchmark
    public int direct_sum() throws Throwable {
        return (int)SUM_HANDLE.invokeExact(1, 2);
    }

    @Benchmark
    public int stable_holder_sum() throws Throwable {
        return (int)SUM_HANDLE_STABLE_HOLDER.orElseThrow().invokeExact(1, 2);
    }

    @Benchmark
    public int non_stable_holder_sum() throws Throwable {
        return (int)SUM_HANDLE_NONSTABLE_HOLDER.methodHandle.invokeExact(1, 2);
    }
}
