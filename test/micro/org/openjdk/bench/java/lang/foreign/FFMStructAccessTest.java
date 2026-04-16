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

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;

// Credit: https://gist.github.com/Spasi/eed94bd2228e637464c32786a52fbd0d

@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" })
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 3, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FFMStructAccessTest {

    private static final int ITERS = 10;

    private static final Unsafe UNSAFE = Utils.unsafe;

    private static final GroupLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT_UNALIGNED.withName("x"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("y"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("z"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("w")
    ).withName("vec4i");

    private static final VarHandle X = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"))
        .withInvokeExactBehavior();
    private static final VarHandle Y = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"))
        .withInvokeExactBehavior();
    private static final VarHandle Z = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("z"))
        .withInvokeExactBehavior();
    private static final VarHandle W = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("w"))
        .withInvokeExactBehavior();
    private static final MemorySegment EVERYTHING = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

    public record Vec4iSegment(MemorySegment segment) {
        int x() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        int y() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        int z() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        int w() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        Vec4iSegment x(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value);
            return this;
        }
        Vec4iSegment y(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value);
            return this;
        }
        Vec4iSegment z(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value);
            return this;
        }
        Vec4iSegment w(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value);
            return this;
        }

        int xVH() { return (int)X.get(segment, 0L); }
        int yVH() { return (int)Y.get(segment, 0L); }
        int zVH() { return (int)Z.get(segment, 0L); }
        int wVH() { return (int)W.get(segment, 0L); }

        Vec4iSegment xVH(int value) {
            X.set(segment, 0L, value);
            return this;
        }
        Vec4iSegment yVH(int value) {
            Y.set(segment, 0L, value);
            return this;
        }
        Vec4iSegment zVH(int value) {
            Z.set(segment, 0L, value);
            return this;
        }
        Vec4iSegment wVH(int value) {
            W.set(segment, 0L, value);
            return this;
        }
    }

    public record Vec4iAddressSegment(long address) {
        private MemorySegment asSegment() {
            return MemorySegment.ofAddress(address).reinterpret(LAYOUT.byteSize());
        }

        int x() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        int y() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        int z() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        int w() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        Vec4iAddressSegment x(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value);
            return this;
        }
        Vec4iAddressSegment y(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value);
            return this;
        }
        Vec4iAddressSegment z(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value);
            return this;
        }
        Vec4iAddressSegment w(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value);
            return this;
        }
    }

    public record Vec4iAddressUnsafe(long address) {
        int x() { return UNSAFE.getIntUnaligned(null, address + 0L); }
        int y() { return UNSAFE.getIntUnaligned(null, address + 4L); }
        int z() { return UNSAFE.getIntUnaligned(null, address + 8L); }
        int w() { return UNSAFE.getIntUnaligned(null, address + 12L); }

        Vec4iAddressUnsafe x(int value) {
            UNSAFE.putIntUnaligned(null, address + 0L, value);
            return this;
        }
        Vec4iAddressUnsafe y(int value) {
            UNSAFE.putIntUnaligned(null, address + 4L, value);
            return this;
        }
        Vec4iAddressUnsafe z(int value) {
            UNSAFE.putIntUnaligned(null, address + 8L, value);
            return this;
        }
        Vec4iAddressUnsafe w(int value) {
            UNSAFE.putIntUnaligned(null, address + 12L, value);
            return this;
        }
    }

    public record Vec4iAddressEverything(long address) {
        int x() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 0L); }
        int y() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 4L); }
        int z() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 8L); }
        int w() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 12L); }

        Vec4iAddressEverything x(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 0L, value);
            return this;
        }
        Vec4iAddressEverything y(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 4L, value);
            return this;
        }
        Vec4iAddressEverything z(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 8L, value);
            return this;
        }
        Vec4iAddressEverything w(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 12L, value);
            return this;
        }
    }

    public record Vec4iBuffer(ByteBuffer buffer) {
        int x() { return buffer.getInt(0); }
        int y() { return buffer.getInt(4); }
        int z() { return buffer.getInt(8); }
        int w() { return buffer.getInt(12); }

        Vec4iBuffer x(int value) {
            buffer.putInt(0, value);
            return this;
        }
        Vec4iBuffer y(int value) {
            buffer.putInt(4, value);
            return this;
        }
        Vec4iBuffer z(int value) {
            buffer.putInt(8, value);
            return this;
        }
        Vec4iBuffer w(int value) {
            buffer.putInt(12, value);
            return this;
        }
    }

    private MemorySegment src = Arena.global().allocate(LAYOUT);
    private MemorySegment dst = Arena.global().allocate(LAYOUT);

    private Vec4iSegment ss = new Vec4iSegment(src);
    private Vec4iSegment ds = new Vec4iSegment(dst);

    private Vec4iAddressSegment sas = new Vec4iAddressSegment(src.address());
    private Vec4iAddressSegment das = new Vec4iAddressSegment(dst.address());

    private Vec4iAddressUnsafe sau = new Vec4iAddressUnsafe(src.address());
    private Vec4iAddressUnsafe dau = new Vec4iAddressUnsafe(dst.address());

    private Vec4iAddressEverything sae = new Vec4iAddressEverything(src.address());
    private Vec4iAddressEverything dae = new Vec4iAddressEverything(dst.address());

    private Vec4iBuffer sb = new Vec4iBuffer(src.asByteBuffer().order(ByteOrder.nativeOrder()));
    private Vec4iBuffer db = new Vec4iBuffer(dst.asByteBuffer().order(ByteOrder.nativeOrder()));

    // ------------------

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t0_copyUnsafeInline16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t0_copyUnsafeInline16() {
        var d = this.dst.address();
        var s = this.src.address();

        for (var i = 0; i < ITERS; i++) {
            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));
        }
    }

    // ------------------

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t1_copySegmentInline16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t1_copySegmentInline16() {
        var dst = this.dst;
        var src = this.src;

        for (var i = 0; i < ITERS; i++) {
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));
        }
    }

    // ------------------

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t2_copyVHInline16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t2_copyVHInline16() {
        var d = this.ds;
        var s = this.ss;

        for (var i = 0; i < ITERS; i++) {
            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));
        }
    }

    // ------------------

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t3_copyUnsafe16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t3_copyUnsafe16() {
        var d = this.dau;
        var s = this.sau;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    // ------------------

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t4_copySegment16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t4_copySegment16() {
        var d = this.ds;
        var s = this.ss;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    // ------------------

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t5_copyVH16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t5_copyVH16() {
        var d = this.ds;
        var s = this.ss;

        for (var i = 0; i < ITERS; i++) {
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());

            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());

            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());

            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
            d.xVH(s.xVH()).yVH(s.yVH()).zVH(s.zVH()).wVH(s.wVH());
        }
    }

    // ------------------

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t6_copyReinterpret16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t6_copyReinterpret16() {
        var d = this.das;
        var s = this.sas;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t7_copyEverything16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t7_copyEverything16() {
        var d = this.dae;
        var s = this.sae;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UnlockDiagnosticVMOptions", "-XX:LogFile=t8_copyBuffer16.xml"})
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void t8_copyBuffer16() {
        var d = this.db;
        var s = this.sb;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

}
