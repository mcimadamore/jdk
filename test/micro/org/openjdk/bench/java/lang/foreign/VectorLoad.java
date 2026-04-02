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
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = {"--enable-preview", "--enable-native-access=ALL-UNNAMED", "--add-modules=jdk.incubator.vector"})
public class VectorLoad {

    static final VectorSpecies<Long> LONG_SPECIES = LongVector.SPECIES_PREFERRED;
    static final int LANES = LONG_SPECIES.length();
    static final int ELEMENTS = LANES * 32;
    static final int LOADS = ELEMENTS / LANES;

    long[] longArray;
    MemorySegment heapSegment;
    MemorySegment nativeSegment;
    int[] randomVectorIndexes;

    @Setup
    public void setup() {
        longArray = new long[ELEMENTS];
        for (int i = 0; i < ELEMENTS; i++) {
            longArray[i] = i + 1L;
        }

        heapSegment = MemorySegment.ofArray(longArray);
        nativeSegment = Arena.global().allocate((long) ELEMENTS * JAVA_LONG.byteSize(), JAVA_LONG.byteAlignment());
        MemorySegment.copy(longArray, 0, nativeSegment, JAVA_LONG, 0, ELEMENTS);

        randomVectorIndexes = new int[LOADS];
        int vectorWindows = (ELEMENTS / LANES);
        Random random = new Random();
        for (int i = 0; i < LOADS; i++) {
            randomVectorIndexes[i] = random.nextInt(vectorWindows) * LANES;
        }
    }

    @Benchmark
    public long vectorLoad_sequential_array() {
        long sum = 0;
        for (int i = 0; i < LOADS; i++) {
            sum ^= LongVector.fromArray(LONG_SPECIES, longArray, i * LANES)
                    .reduceLanes(VectorOperators.XOR);
        }
        return sum;
    }

    @Benchmark
    public long vectorLoad_sequential_heap_segment() {
        long sum = 0;
        for (int i = 0; i < LOADS; i++) {
            sum ^= LongVector.fromMemorySegment(LONG_SPECIES, heapSegment,
                    (long) i * LANES * JAVA_LONG.byteSize(), ByteOrder.nativeOrder())
                    .reduceLanes(VectorOperators.XOR);
        }
        return sum;
    }

    @Benchmark
    public long vectorLoad_sequential_native_segment() {
        long sum = 0;
        for (int i = 0; i < LOADS; i++) {
            sum ^= LongVector.fromMemorySegment(LONG_SPECIES, nativeSegment,
                    (long) i * LANES * JAVA_LONG.byteSize(), ByteOrder.nativeOrder())
                    .reduceLanes(VectorOperators.XOR);
        }
        return sum;
    }

    @Benchmark
    public long vectorLoad_random_array() {
        long sum = 0;
        for (int i = 0; i < LOADS; i++) {
            sum ^= LongVector.fromArray(LONG_SPECIES, longArray, randomVectorIndexes[i])
                    .reduceLanes(VectorOperators.XOR);
        }
        return sum;
    }

    @Benchmark
    public long vectorLoad_random_heap_segment() {
        long sum = 0;
        for (int i = 0; i < LOADS; i++) {
            sum ^= LongVector.fromMemorySegment(LONG_SPECIES, heapSegment,
                    (long) randomVectorIndexes[i] * JAVA_LONG.byteSize(), ByteOrder.nativeOrder())
                    .reduceLanes(VectorOperators.XOR);
        }
        return sum;
    }

    @Benchmark
    public long vectorLoad_random_native_segment() {
        long sum = 0;
        for (int i = 0; i < LOADS; i++) {
            sum ^= LongVector.fromMemorySegment(LONG_SPECIES, nativeSegment,
                    (long) randomVectorIndexes[i] * JAVA_LONG.byteSize(), ByteOrder.nativeOrder())
                    .reduceLanes(VectorOperators.XOR);
        }
        return sum;
    }
}
