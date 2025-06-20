package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import jdk.internal.misc.Unsafe;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class FMASerDeOffHeapReinterpret {

    private static final Unsafe UNSAFE = Utils.unsafe;

    long bufferUnsafe;

    @Benchmark
    public void unsafeWriteSingle() {
        UNSAFE.putInt(bufferUnsafe, 3);
    }

    @Benchmark
    public void unsafeWriteLoop_10() {
        for (int i = 0 ; i < 40 ; i+=4) {
            UNSAFE.putInt(bufferUnsafe + i, 3);
        }
    }

    @Benchmark
    public void unsafeWriteLoop_100() {
        for (int i = 0 ; i < 400 ; i+=4) {
            UNSAFE.putInt(bufferUnsafe + i, 3);
        }
    }

    @Benchmark
    public void unsafeWriteLoop_1000() {
        for (int i = 0 ; i < 4000 ; i+=4) {
            UNSAFE.putInt(bufferUnsafe + i, 3);
        }
    }

    @Benchmark
    public void unsafeReadSingle(Blackhole blackhole) {
        blackhole.consume(UNSAFE.getInt(bufferUnsafe));
    }

    @Benchmark
    public void unsafeReadLoop_10(Blackhole blackhole) {
        for (int i = 0 ; i < 40 ; i+=4) {
            blackhole.consume(UNSAFE.getInt(bufferUnsafe + i));
        }
    }

    @Benchmark
    public void unsafeReadLoop_100(Blackhole blackhole) {
        for (int i = 0 ; i < 400 ; i+=4) {
            blackhole.consume(UNSAFE.getInt(bufferUnsafe + i));
        }
    }

    @Benchmark
    public void unsafeReadLoop_1000(Blackhole blackhole) {
        for (int i = 0 ; i < 4000 ; i+=4) {
            blackhole.consume(UNSAFE.getInt(bufferUnsafe + i));
        }
    }

    @Benchmark
    public void fmaWriteSingle() {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        memSegment.set(ValueLayout.JAVA_INT_UNALIGNED, 0, 3);
    }

    @Benchmark
    public void fmaWriteLoop_10() {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        for (int i = 0 ; i < 40 ; i+=4) {
            memSegment.set(ValueLayout.JAVA_INT_UNALIGNED, i, 3);
        }
    }

    @Benchmark
    public void fmaWriteLoop_100() {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        for (int i = 0 ; i < 400 ; i+=4) {
            memSegment.set(ValueLayout.JAVA_INT_UNALIGNED, i, 3);
        }
    }

    @Benchmark
    public void fmaWriteLoop_1000() {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        for (int i = 0 ; i < 4000 ; i+=4) {
            memSegment.set(ValueLayout.JAVA_INT_UNALIGNED, i, 3);
        }
    }

    @Benchmark
    public void fmaReadSingle(Blackhole blackhole) {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        blackhole.consume(memSegment.get(ValueLayout.JAVA_INT_UNALIGNED, 0));
    }

    @Benchmark
    public void fmaReadLoop_10(Blackhole blackhole) {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        for (int i = 0 ; i < 40 ; i+=4) {
            blackhole.consume(memSegment.get(ValueLayout.JAVA_INT_UNALIGNED, i));
        }
    }

    @Benchmark
    public void fmaReadLoop_100(Blackhole blackhole) {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        for (int i = 0 ; i < 400 ; i+=4) {
            blackhole.consume(memSegment.get(ValueLayout.JAVA_INT_UNALIGNED, i));
        }
    }

    @Benchmark
    public void fmaReadLoop_1000(Blackhole blackhole) {
        MemorySegment memSegment = MemorySegment.ofAddress(bufferUnsafe).reinterpret(4000);
        for (int i = 0 ; i < 4000 ; i+=4) {
            blackhole.consume(memSegment.get(ValueLayout.JAVA_INT_UNALIGNED, i));
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        bufferUnsafe = UNSAFE.allocateMemory(4000);
    }

    @TearDown
    public void teardown() {
        UNSAFE.freeMemory(bufferUnsafe);
    }
}
