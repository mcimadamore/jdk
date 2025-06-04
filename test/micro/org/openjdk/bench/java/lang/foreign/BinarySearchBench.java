package org.openjdk.bench.java.lang.foreign;

import jdk.internal.misc.Unsafe;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentCursor;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class BinarySearchBench {
    static final Unsafe unsafe = Utils.unsafe;

    final static int CARRIER_SIZE = 4;
    final static int ALLOC_SIZE = CARRIER_SIZE * 1024;
    final static int ELEM_SIZE = ALLOC_SIZE / CARRIER_SIZE;

    Arena arena;
    MemorySegment segment;
    long address;

    int toFind = 42;

    @Setup
    public void setup() {
        address = unsafe.allocateMemory(ALLOC_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(address + (i * CARRIER_SIZE), i);
        }
        arena = Arena.ofConfined();
        segment = arena.allocate(ALLOC_SIZE, CARRIER_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT_UNALIGNED, i, i);
        }
    }

    @TearDown
    public void tearDown() throws Throwable {
        unsafe.freeMemory(address);
        arena.close();
    }

    @Benchmark
    public long binarySearchSimpleUnsafe() {
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = unsafe.getIntUnaligned(null, address + mid);
            if (curr == toFind) {
                return check(mid / 4, toFind);
            } else if (curr > toFind) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchSimpleSegment() {
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = segment.get(ValueLayout.JAVA_INT_UNALIGNED, mid);
            if (curr == toFind) {
                return check(mid / 4, toFind);
            } else if (curr > toFind) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchBranchlessUnsafe() {
        long ret = 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 9)) <= toFind ? CARRIER_SIZE << 9 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 8)) <= toFind ? CARRIER_SIZE << 8 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 7)) <= toFind ? CARRIER_SIZE << 7 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 6)) <= toFind ? CARRIER_SIZE << 6 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 5)) <= toFind ? CARRIER_SIZE << 5 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 4)) <= toFind ? CARRIER_SIZE << 4 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 3)) <= toFind ? CARRIER_SIZE << 3 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 2)) <= toFind ? CARRIER_SIZE << 2 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 1)) <= toFind ? CARRIER_SIZE << 1 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + CARRIER_SIZE) <= toFind ? CARRIER_SIZE : 0;
        return check(ret / 4, toFind);
    }

    @Benchmark
    public long binarySearchBranchlessSegment() {
        long ret = 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 9)) <= toFind ? CARRIER_SIZE << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 8)) <= toFind ? CARRIER_SIZE << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 7)) <= toFind ? CARRIER_SIZE << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 6)) <= toFind ? CARRIER_SIZE << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 5)) <= toFind ? CARRIER_SIZE << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 4)) <= toFind ? CARRIER_SIZE << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 3)) <= toFind ? CARRIER_SIZE << 3 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 2)) <= toFind ? CARRIER_SIZE << 2 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 1)) <= toFind ? CARRIER_SIZE << 1 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + CARRIER_SIZE) <= toFind ? CARRIER_SIZE : 0;
        return check(ret / 4, toFind);
    }

    @Benchmark
    public long binarySearchCursorSegment() {
        SegmentCursor cursor = SegmentCursor.of(segment, SegmentCursor.MID);
        while (true) {
            int curr = cursor.get(ValueLayout.JAVA_INT_UNALIGNED);
            if (curr == toFind) {
                return check(cursor.offset() / 4, toFind);
            } else if (curr > toFind) {
                cursor = cursor.left();
            } else {
                cursor = cursor.right();
            }
        }
    }

    long check(long found, long expected) {
        if (found != expected) throw new AssertionError("Found: " + found + " Expected: " + expected);
        return found;
    }
}
