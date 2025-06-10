package org.openjdk.bench.java.lang.foreign;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
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
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "--add-modules=jdk.incubator.vector"})
public class BinarySearchBench {

    static {
        System.out.println("SPECIES = " + VectorSpecies.ofPreferred(int.class));
    }

    static final Unsafe unsafe = Utils.unsafe;

    final static int CARRIER_SIZE = 4;
    final static int ALLOC_SIZE = CARRIER_SIZE * 1024;
    final static int ELEM_SIZE = ALLOC_SIZE / CARRIER_SIZE;

    final static Random random = new Random(47);

    Arena arena;
    MemorySegment segment;
    long address;
    Result result;

    record Result(int index, int value) {
        static Result of(MemorySegment segment) {
            int index = random.nextInt(ELEM_SIZE);
            int value = segment.getAtIndex(ValueLayout.JAVA_INT_UNALIGNED, index);
            return new Result(index, value);
        }
    }

    @Setup
    public void setup() {
        arena = Arena.ofConfined();
        int[] values = new int[ELEM_SIZE];
        for (int i = 0; i < ELEM_SIZE; i++) {
            values[i] = random.nextInt();
        }
        Arrays.sort(values);
        segment = arena.allocate(ALLOC_SIZE, CARRIER_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT_UNALIGNED, i, values[i]);
        }
        result = Result.of(segment);
        address = segment.address();
    }

    @TearDown
    public void tearDown() throws Throwable {
        arena.close();
    }

    @Benchmark
    public long binarySearchSimpleUnsafe() {
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = unsafe.getIntUnaligned(null, address + mid);
            if (curr == result.value) {
                return check(mid / 4, result.index);
            } else if (curr > result.value) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchSimpleDirectSegment() {
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = segment.get(ValueLayout.JAVA_INT_UNALIGNED, mid);
            if (curr == result.value) {
                return check(mid / 4, result.index);
            } else if (curr > result.value) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchSimpleSlicedSegment() {
        MemorySegment segment = this.segment.asSlice(0, ALLOC_SIZE);
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = segment.get(ValueLayout.JAVA_INT_UNALIGNED, mid);
            if (curr == result.value) {
                return check(mid / 4, result.index);
            } else if (curr > result.value) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchSimpleReinterpretedSegment() {
        MemorySegment segment = this.segment.reinterpret(ALLOC_SIZE, Arena.global(), null);
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = segment.get(ValueLayout.JAVA_INT_UNALIGNED, mid);
            if (curr == result.value) {
                return check(mid / 4, result.index);
            } else if (curr > result.value) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchBranchlessUnsafe() {
        long ret = 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 9)) <= result.value ? CARRIER_SIZE << 9 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 8)) <= result.value ? CARRIER_SIZE << 8 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 7)) <= result.value ? CARRIER_SIZE << 7 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 6)) <= result.value ? CARRIER_SIZE << 6 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 5)) <= result.value ? CARRIER_SIZE << 5 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 4)) <= result.value ? CARRIER_SIZE << 4 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 3)) <= result.value ? CARRIER_SIZE << 3 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 2)) <= result.value ? CARRIER_SIZE << 2 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 1)) <= result.value ? CARRIER_SIZE << 1 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + CARRIER_SIZE) <= result.value ? CARRIER_SIZE : 0;
        return check(ret / 4, result.index);
    }

    @Benchmark
    public long binarySearchBranchlessVectorUnsafe() {
        long ret = 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 9)) <= result.value ? CARRIER_SIZE << 9 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 8)) <= result.value ? CARRIER_SIZE << 8 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 7)) <= result.value ? CARRIER_SIZE << 7 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 6)) <= result.value ? CARRIER_SIZE << 6 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 5)) <= result.value ? CARRIER_SIZE << 5 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 4)) <= result.value ? CARRIER_SIZE << 4 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 3)) <= result.value ? CARRIER_SIZE << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, MemorySegment.ofAddress(address).reinterpret(ALLOC_SIZE), ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, result.value);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), result.index);
    }

    @Benchmark
    public long binarySearchBranchlessDirectSegment() {
        long ret = 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 9)) <= result.value ? CARRIER_SIZE << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 8)) <= result.value ? CARRIER_SIZE << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 7)) <= result.value ? CARRIER_SIZE << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 6)) <= result.value ? CARRIER_SIZE << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 5)) <= result.value ? CARRIER_SIZE << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 4)) <= result.value ? CARRIER_SIZE << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 3)) <= result.value ? CARRIER_SIZE << 3 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 2)) <= result.value ? CARRIER_SIZE << 2 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 1)) <= result.value ? CARRIER_SIZE << 1 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + CARRIER_SIZE) <= result.value ? CARRIER_SIZE : 0;
        return check(ret / 4, result.index);
    }

    @Benchmark
    public long binarySearchBranchlessSlicedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.asSlice(0, ALLOC_SIZE);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 9)) <= result.value ? CARRIER_SIZE << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 8)) <= result.value ? CARRIER_SIZE << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 7)) <= result.value ? CARRIER_SIZE << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 6)) <= result.value ? CARRIER_SIZE << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 5)) <= result.value ? CARRIER_SIZE << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 4)) <= result.value ? CARRIER_SIZE << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 3)) <= result.value ? CARRIER_SIZE << 3 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 2)) <= result.value ? CARRIER_SIZE << 2 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 1)) <= result.value ? CARRIER_SIZE << 1 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + CARRIER_SIZE) <= result.value ? CARRIER_SIZE : 0;
        return check(ret / 4, result.index);
    }

    @Benchmark
    public long binarySearchBranchlessReinterpretedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.reinterpret(ALLOC_SIZE, Arena.global(), null);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 9)) <= result.value ? CARRIER_SIZE << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 8)) <= result.value ? CARRIER_SIZE << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 7)) <= result.value ? CARRIER_SIZE << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 6)) <= result.value ? CARRIER_SIZE << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 5)) <= result.value ? CARRIER_SIZE << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 4)) <= result.value ? CARRIER_SIZE << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 3)) <= result.value ? CARRIER_SIZE << 3 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 2)) <= result.value ? CARRIER_SIZE << 2 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 1)) <= result.value ? CARRIER_SIZE << 1 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + CARRIER_SIZE) <= result.value ? CARRIER_SIZE : 0;
        return check(ret / 4, result.index);
    }

    final static VectorSpecies<Integer> SPECIES = VectorSpecies.of(int.class, VectorShape.forBitSize(32 * 8));

    @Benchmark
    public long binarySearchBranchlessVectorDirectSegment() {
        long ret = 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 9)) <= result.value ? 4 << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 8)) <= result.value ? 4 << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 7)) <= result.value ? 4 << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 6)) <= result.value ? 4 << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 5)) <= result.value ? 4 << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 4)) <= result.value ? 4 << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 3)) <= result.value ? 4 << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, segment, ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, result.value);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), result.index);
    }

    @Benchmark
    public long binarySearchBranchlessVectorSlicedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.asSlice(0, ALLOC_SIZE);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 9)) <= result.value ? 4 << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 8)) <= result.value ? 4 << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 7)) <= result.value ? 4 << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 6)) <= result.value ? 4 << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 5)) <= result.value ? 4 << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 4)) <= result.value ? 4 << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 3)) <= result.value ? 4 << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, segment, ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, result.value);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), result.index);
    }

    @Benchmark
    public long binarySearchBranchlessVectorReinterpretedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.reinterpret(ALLOC_SIZE, Arena.global(), null);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 9)) <= result.value ? 4 << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 8)) <= result.value ? 4 << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 7)) <= result.value ? 4 << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 6)) <= result.value ? 4 << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 5)) <= result.value ? 4 << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 4)) <= result.value ? 4 << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 3)) <= result.value ? 4 << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, segment, ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, result.value);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), result.index);
    }

    long check(long found, long expected) {
        if (found != expected) throw new AssertionError("Found: " + found + " Expected: " + expected);
        return found;
    }
}
