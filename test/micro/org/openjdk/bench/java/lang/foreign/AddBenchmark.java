package org.openjdk.bench.java.lang.foreign;

import sun.misc.Unsafe;

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.DoubleBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark the element wise aggregation of an array
 * of doubles into another array of doubles, using
 * combinations of  java arrays, byte buffers, standard java code
 * and the new Vector API.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class AddBenchmark {

    static final ValueLayout.OfDouble JAVA_DOUBLE = ValueLayout.JAVA_DOUBLE;

    static final Arena GLOBAL_ARENA = Arena.global();

    static final Unsafe U = Utils.unsafe;

    final static int SIZE = 1024;

    @State(Scope.Benchmark)
    public static class Data {
        final double[] inputArray;
        final double[] outputArray;
        final MemorySegment inputSegment;
        final MemorySegment outputSegment;
        final DoubleBuffer inputBuffer;
        final DoubleBuffer outputBuffer;
        final long inputAddress;
        final long outputAddress;

        public Data() {
            this.inputArray = new double[SIZE];
            this.outputArray = new double[SIZE];

            this.inputSegment = GLOBAL_ARENA.allocate(8 * SIZE);
            this.outputSegment = GLOBAL_ARENA.allocate(8 * SIZE);

            this.inputAddress = U.allocateMemory(8 * SIZE);
            this.outputAddress = U.allocateMemory(8 * SIZE);

            this.inputBuffer = DoubleBuffer.allocate(SIZE);
            this.outputBuffer = DoubleBuffer.allocate(SIZE);
        }
    }

    @Benchmark
    public void scalarArrayArray(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < input.length; i++) {
            output[i] += input[i];
        }
    }

    @Benchmark
    public void scalarArrayArrayLongStride(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        // long stride defeats automatic unrolling
        for(long i = 0; i < input.length; i+=1L) {
            output[(int) i] += input[(int) i];
        }
    }

    @Benchmark
    public void scalarSegmentSegment(Data state) {
        final MemorySegment input = state.inputSegment;
        final MemorySegment output = state.outputSegment;
        for(int i = 0; i < SIZE; i++) {
            output.setAtIndex(JAVA_DOUBLE, i, output.getAtIndex(JAVA_DOUBLE, i) + input.getAtIndex(JAVA_DOUBLE, i));
        }
    }

    @Benchmark
    public void scalarSegmentSegmentLongStride(Data state) {
        final MemorySegment input = state.inputSegment;
        final MemorySegment output = state.outputSegment;
        for(long i = 0; i < SIZE; i++) {
            output.setAtIndex(JAVA_DOUBLE, i, output.getAtIndex(JAVA_DOUBLE, i) + input.getAtIndex(JAVA_DOUBLE, i));
        }
    }

    @Benchmark
    public void scalarBufferArray(Data state) {
        final DoubleBuffer input = state.inputBuffer;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i++) {
            output[i] += input.get(i);
        }
    }

    @Benchmark
    public void scalarBufferBuffer(Data state) {
        final DoubleBuffer input = state.inputBuffer;
        final DoubleBuffer output = state.outputBuffer;
        for(int i = 0; i < SIZE; i++) {
            output.put(i, output.get(i) + input.get(i));
        }
    }

    @Benchmark
    public void scalarSegmentArray(Data state) {
        final MemorySegment input = state.inputSegment;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i++) {
            output[i] += input.getAtIndex(JAVA_DOUBLE, i);
        }
    }

    @Benchmark
    public void scalarUnsafeArray(Data state) {
        final long ia = state.inputAddress;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i++) {
            output[i] += U.getDouble(ia + 8*i);
        }
    }

    @Benchmark
    public void scalarUnsafeUnsafe(Data state) {
        final long ia = state.inputAddress;
        final long oa = state.outputAddress;
        for(int i = 0; i < SIZE; i++) {
            U.putDouble(oa + 8*i, U.getDouble(ia + 8*i) + U.getDouble(oa + 8*i));
        }
    }


}
