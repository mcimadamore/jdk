/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(100)
@SuppressWarnings("deprecation")
public class VarHandleSegmentStartup {

    final MemorySegment segment = Arena.ofAuto().allocate(ValueLayout.JAVA_LONG);

    static final VarHandle Z_HANDLE = ValueLayout.JAVA_BOOLEAN.varHandle();
    static final VarHandle C_HANDLE = ValueLayout.JAVA_CHAR.varHandle();
    static final VarHandle S_HANDLE = ValueLayout.JAVA_SHORT.varHandle();
    static final VarHandle I_HANDLE = ValueLayout.JAVA_INT.varHandle();
    static final VarHandle J_HANDLE = ValueLayout.JAVA_LONG.varHandle();
    static final VarHandle F_HANDLE = ValueLayout.JAVA_FLOAT.varHandle();
    static final VarHandle D_HANDLE = ValueLayout.JAVA_DOUBLE.varHandle();
    static final VarHandle A_HANDLE = ValueLayout.ADDRESS.varHandle();

    // getters

    @Benchmark
    public boolean vh_bool_get() {
        return (boolean)Z_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public boolean vh_bool_get_no_guard() {
        return (boolean)Z_HANDLE.get(segment, 0L);
    }

    @Benchmark
    public char vh_char_get() {
        return (char)C_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public char vh_char_get_no_guard() {
        return (char)C_HANDLE.get(segment, 0L);
    }

    @Benchmark
    public short vh_short_get() {
        return (short)S_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public short vh_short_get_no_guard() {
        return (short)S_HANDLE.get(segment, 0L);
    }

    @Benchmark
    public int vh_int_get() {
        return (int)I_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public int vh_int_get_no_guard() {
        return (int)I_HANDLE.get(segment, 0L);
    }

    @Benchmark
    public long vh_long_get() {
        return (long)J_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public long vh_long_get_no_guard() {
        return (long)J_HANDLE.get(segment, 0L);
    }

    @Benchmark
    public float vh_float_get() {
        return (float) F_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public float vh_float_get_no_guard() {
        return (float) F_HANDLE.get(segment, 0L);
    }

    @Benchmark
    public double vh_double_get() {
        return (double) D_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public double vh_double_get_no_guard() {
        return (double) D_HANDLE.get(segment, 0L);
    }

    @Benchmark
    public MemorySegment vh_addr_get() {
        return (MemorySegment) A_HANDLE.get(segment, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public MemorySegment vh_addr_get_no_guard() {
        return (MemorySegment) A_HANDLE.get(segment, 0L);
    }

    // setters

    @Benchmark
    public void vh_bool_set() {
        Z_HANDLE.set(segment, 0L, true);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_bool_set_no_guard() {
        Z_HANDLE.set(segment, 0L, true);
    }

    @Benchmark
    public void vh_char_set() {
        C_HANDLE.set(segment, 0L, 'c');
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_char_set_no_guard() {
        C_HANDLE.set(segment, 0L, 'c');
    }

    @Benchmark
    public void vh_short_set() {
        S_HANDLE.set(segment, 0L, (short)0);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_short_set_no_guard() {
        S_HANDLE.set(segment, 0L, (short)0);
    }

    @Benchmark
    public void vh_int_set() {
        I_HANDLE.set(segment, 0L, 0);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_int_set_no_guard() {
        I_HANDLE.set(segment, 0L, 0);
    }

    @Benchmark
    public void vh_long_set() {
        J_HANDLE.set(segment, 0L, 0L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_long_set_no_guard() {
        J_HANDLE.set(segment, 0L, 0L);
    }

    @Benchmark
    public void vh_float_set() {
        F_HANDLE.set(segment, 0L, 0f);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_float_set_no_guard() {
        F_HANDLE.set(segment, 0L, 0f);
    }

    @Benchmark
    public void vh_double_set() {
        D_HANDLE.set(segment, 0L, 0d);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_double_set_no_guard() {
        D_HANDLE.set(segment, 0L, 0d);
    }

    @Benchmark
    public void vh_addr_set() {
        A_HANDLE.set(segment, 0L, MemorySegment.NULL);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false")
    public void vh_addr_set_no_guard() {
        A_HANDLE.set(segment, 0L, MemorySegment.NULL);
    }
}
