package org.openjdk.bench.java.lang.foreign;

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class OffHeapAccessLoop {

    private static final Unsafe UNSAFE = Utils.unsafe;

    @Param({"1", "10", "100", "1000"})
    int elems;
    int bytes;

    static final int ELEM_SIZE = (int)ValueLayout.JAVA_INT_UNALIGNED.byteSize();

    long address;
    MemorySegment segment;

    @Benchmark
    public void unsafeWriteLoop() {
        for (int i = 0 ; i < bytes ; i += ELEM_SIZE) {
            UNSAFE.putIntUnaligned(null, address + i, 3);
        }
    }

    @Benchmark
    public void unsafeReadLoop(Blackhole blackhole) {
        for (int i = 0 ; i < bytes ; i += ELEM_SIZE) {
            blackhole.consume(UNSAFE.getInt(address + i));
        }
    }

    @Benchmark
    public void segmentWriteLoop() {
        for (int i = 0 ; i < bytes ; i += ELEM_SIZE) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, i, 3);
        }
    }

    @Benchmark
    public void segmentReadLoop(Blackhole blackhole) {
        for (int i = 0 ; i < bytes ; i += ELEM_SIZE) {
            blackhole.consume(segment.get(ValueLayout.JAVA_INT_UNALIGNED, i));
        }
    }

    @Benchmark
    public void segmentWriteLoopReinterpret() {
        MemorySegment slice = MemorySegment.ofAddress(address).reinterpret(bytes);
        for (int i = 0 ; i < bytes ; i += ELEM_SIZE) {
            slice.set(ValueLayout.JAVA_INT_UNALIGNED, i, 3);
        }
    }

    @Benchmark
    public void segmentReadLoopReinterpret(Blackhole blackhole) {
        MemorySegment slice = MemorySegment.ofAddress(address).reinterpret(bytes);
        for (int i = 0 ; i < bytes ; i += ELEM_SIZE) {
            blackhole.consume(slice.get(ValueLayout.JAVA_INT_UNALIGNED, i));
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        bytes = elems * (int)ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        address = UNSAFE.allocateMemory(bytes);
        segment = MemorySegment.ofAddress(address).reinterpret(bytes);
    }

    @TearDown
    public void teardown() {
        UNSAFE.freeMemory(address);
    }
}
