package org.openjdk.bench.java.lang;

import jdk.internal.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@Fork(value = 3, jvmArgsAppend = {"--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})

/// This benchmark compares direct field access vs. Unsafe vs. VarHandle. More specifically, we compare
/// access to field whose type is an interface type against access to a field whose type is a class type.
/// The benchmark shows that non-direct access to interface fields is slower than direct access:
///
/// ```
/// Benchmark                               Mode  Cnt  Score   Error  Units
/// UnsafeFieldAccessTest.classFieldDirect  avgt    9  0.470 ± 0.015  ns/op
/// UnsafeFieldAccessTest.classFieldHandle  avgt    9  0.469 ± 0.013  ns/op
/// UnsafeFieldAccessTest.classFieldUnsafe  avgt    9  0.471 ± 0.019  ns/op
/// UnsafeFieldAccessTest.ifaceFieldDirect  avgt    9  0.474 ± 0.013  ns/op
/// UnsafeFieldAccessTest.ifaceFieldHandle  avgt    9  0.735 ± 0.012  ns/op
/// UnsafeFieldAccessTest.ifaceFieldUnsafe  avgt    9  0.686 ± 0.022  ns/op
/// ```
///
/// Looking at the assembly, the difference seems to be explained by an additional checkcast in the interface access path:
///
/// ```
/// mov r11d, [rsi+0xc]    ; compressed oop: ifaceList
/// ... load klass
/// cmp ..., ArrayList
/// jne uncommon_trap       ; checkcast
/// ```
///
/// Instead of much simpler code in the class access path:
///
/// ```
/// mov r11d, [rsi+0x8]    ; compressed oop: classList
/// ```
///
/// In other words, the cast present in the source code can only be eliminated by C2 if the field type is a class type.
public class UnsafeFieldAccessTest {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final ArrayList<String> classList = new ArrayList<>(List.of("one", "two", "three"));
    final List<String> ifaceList = new ArrayList<>(List.of("one", "two", "three"));

    static final long CLASS_LIST_OFFSET = UNSAFE.objectFieldOffset(UnsafeFieldAccessTest.class, "classList");
    static final long IFACE_LIST_OFFSET = UNSAFE.objectFieldOffset(UnsafeFieldAccessTest.class, "ifaceList");

    static final VarHandle CLASS_LIST_VH, IFACE_LIST_VH;

    static {
        try {
            CLASS_LIST_VH = MethodHandles.lookup().findVarHandle(UnsafeFieldAccessTest.class, "classList", ArrayList.class);
            IFACE_LIST_VH = MethodHandles.lookup().findVarHandle(UnsafeFieldAccessTest.class, "ifaceList", List.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Benchmark
    public void classFieldDirect(Blackhole blackhole) {
        blackhole.consume(classList);
    }

    @Benchmark
    public void ifaceFieldDirect(Blackhole blackhole) {
        blackhole.consume(ifaceList);
    }

    @Benchmark
    public void classFieldUnsafe(Blackhole blackhole) {
        blackhole.consume((ArrayList<?>)UNSAFE.getReference(this, CLASS_LIST_OFFSET));
    }

    @Benchmark
    public void ifaceFieldUnsafe(Blackhole blackhole) {
        blackhole.consume((List<?>)UNSAFE.getReference(this, IFACE_LIST_OFFSET));
    }

    @Benchmark
    public void classFieldHandle(Blackhole blackhole) {
        blackhole.consume((ArrayList<?>)CLASS_LIST_VH.get(this));
    }

    @Benchmark
    public void ifaceFieldHandle(Blackhole blackhole) {
        blackhole.consume((List<?>)IFACE_LIST_VH.get(this));
    }
}
