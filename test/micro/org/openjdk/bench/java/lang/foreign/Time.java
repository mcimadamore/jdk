/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.Addressable;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
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
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--enable-preview" })
public class Time {

    static {
        System.loadLibrary("Time");
    }

    static final Linker LINKER = Linker.nativeLinker();
    static final MemorySegment CLOCK_GETTIME_ADDR = LINKER.defaultLookup().lookup("clock_gettime").get();

    static final MethodHandle CLOCK_GETTIME = LINKER.downcallHandle(
            CLOCK_GETTIME_ADDR,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    );

    static final MethodHandle CLOCK_GETTIME_TRIVIAL = LINKER.downcallHandle(
            CLOCK_GETTIME_ADDR,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS).asTrivial()
    );

    static final GroupLayout TIMESPEC = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("tv_sec"),
            ValueLayout.JAVA_LONG.withName("tv_nsec")
    );

    static final VarHandle TIMESPEC_TV_SEC = TIMESPEC.varHandle(PathElement.groupElement("tv_sec"));
    static final VarHandle TIMESPEC_TV_NSEC = TIMESPEC.varHandle(PathElement.groupElement("tv_nsec"));

    final MemorySegment timespec = MemorySegment.allocateNative(TIMESPEC, MemorySession.openImplicit());

    @Param
    public ClockType clockType;

    public enum ClockType {
        CLOCK_REALTIME(0),
        CLOCK_REALTIME_COARSE(5),
        CLOCK_MONOTONIC(1),
        CLOCK_MONOTONIC_COARSE(6),
        CLOCK_MONOTONIC_RAW(4);

        final int clockType;

        ClockType(int clockType) {
            this.clockType = clockType;
        }

        int type() {
            return clockType;
        }
    }

    @Benchmark
    public long time_jni() {
        return gettime(clockType.type());
    }

    @Benchmark
    public long time_panama() throws Throwable {
        int res = (int)CLOCK_GETTIME.invokeExact(clockType.type(), (Addressable)timespec);
        long tv_sec = (long)TIMESPEC_TV_SEC.get(timespec);
        long tv_nsec = (long)TIMESPEC_TV_NSEC.get(timespec);
        return tv_sec * 1000000000L + tv_nsec;
    }

    @Benchmark
    public long time_panama_trivial() throws Throwable {
        int res = (int)CLOCK_GETTIME_TRIVIAL.invokeExact(clockType.type(), (Addressable)timespec);
        long tv_sec = (long)TIMESPEC_TV_SEC.get(timespec);
        long tv_nsec = (long)TIMESPEC_TV_NSEC.get(timespec);
        return tv_sec * 1000000000L + tv_nsec;
    }

    static native long gettime(int clocktype);
}
