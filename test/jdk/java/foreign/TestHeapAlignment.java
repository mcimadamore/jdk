/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestHeapAlignment
 */

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ValueLayout;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestHeapAlignment {

    static final ValueLayout.OfChar JAVA_CHAR_ALIGNED = ValueLayout.JAVA_CHAR.withBitAlignment(16);
    static final ValueLayout.OfShort JAVA_SHORT_ALIGNED = ValueLayout.JAVA_SHORT.withBitAlignment(16);
    static final ValueLayout.OfInt JAVA_INT_ALIGNED = ValueLayout.JAVA_INT.withBitAlignment(32);
    static final ValueLayout.OfFloat JAVA_FLOAT_ALIGNED = ValueLayout.JAVA_FLOAT.withBitAlignment(32);
    static final ValueLayout.OfLong JAVA_LONG_ALIGNED = ValueLayout.JAVA_LONG.withBitAlignment(64);
    static final ValueLayout.OfDouble JAVA_DOUBLE_ALIGNED = ValueLayout.JAVA_DOUBLE.withBitAlignment(64);

    @Test
    public void testStoreIntoByteArray() {
        var segment = MemorySegment.ofArray(new byte[8]);
        segment.set(ValueLayout.JAVA_BYTE, 0, (byte)42);
        segment.set(ValueLayout.JAVA_BOOLEAN, 0, true);
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_CHAR_ALIGNED, 0, (char)42));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_SHORT_ALIGNED, 0, (short)42));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_INT_ALIGNED, 0, 42));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_FLOAT_ALIGNED, 0, 42f));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_LONG_ALIGNED, 0, 42L));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_DOUBLE_ALIGNED, 0, 42d));
    }

    @Test
    public void testStoreIntoShortArray() {
        var segment = MemorySegment.ofArray(new short[4]);
        segment.set(ValueLayout.JAVA_BYTE, 0, (byte)42);
        segment.set(ValueLayout.JAVA_BOOLEAN, 0, true);
        segment.set(JAVA_CHAR_ALIGNED, 0, (char)42);
        segment.set(JAVA_SHORT_ALIGNED, 0, (short)42);
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_INT_ALIGNED, 0, 42));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_FLOAT_ALIGNED, 0, 42f));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_LONG_ALIGNED, 0, 42L));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_DOUBLE_ALIGNED, 0, 42d));
    }

    @Test
    public void testStoreIntoIntArray() {
        var segment = MemorySegment.ofArray(new int[2]);
        segment.set(ValueLayout.JAVA_BYTE, 0, (byte)42);
        segment.set(ValueLayout.JAVA_BOOLEAN, 0, true);
        segment.set(JAVA_CHAR_ALIGNED, 0, (char)42);
        segment.set(JAVA_SHORT_ALIGNED, 0, (short)42);
        segment.set(JAVA_INT_ALIGNED, 0, 42);
        segment.set(JAVA_FLOAT_ALIGNED, 0, 42f);
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_LONG_ALIGNED, 0, 42L));
        Assert.assertThrows(IllegalStateException.class, () -> segment.set(JAVA_DOUBLE_ALIGNED, 0, 42d));
    }

    @Test
    public void testStoreIntoLongArray() {
        var segment = MemorySegment.ofArray(new long[1]);
        segment.set(ValueLayout.JAVA_BYTE, 0, (byte)42);
        segment.set(ValueLayout.JAVA_BOOLEAN, 0, true);
        segment.set(JAVA_CHAR_ALIGNED, 0, (char)42);
        segment.set(JAVA_SHORT_ALIGNED, 0, (short)42);
        segment.set(JAVA_INT_ALIGNED, 0, 42);
        segment.set(JAVA_FLOAT_ALIGNED, 0, 42f);
        segment.set(JAVA_LONG_ALIGNED, 0, 42L);
        segment.set(JAVA_DOUBLE_ALIGNED, 0, 42d);
    }

    @Test
    public void testCopyIntoByteArray() {
        var segment = MemorySegment.ofArray(new byte[8]);
        MemorySegment.copy(new byte[] { 42 }, 0, segment, ValueLayout.JAVA_BYTE, 0, 1);
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new char[] { 42 }, 0, segment, JAVA_CHAR_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new short[] { 42 }, 0, segment, JAVA_SHORT_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new int[] { 42 }, 0, segment, JAVA_INT_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new float[] { 42 }, 0, segment, JAVA_FLOAT_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new long[] { 42 }, 0, segment, JAVA_LONG_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new double[] { 42 }, 0, segment, JAVA_DOUBLE_ALIGNED, 0, 1));
    }

    @Test
    public void testCopyIntoShortArray() {
        var segment = MemorySegment.ofArray(new short[4]);
        MemorySegment.copy(new byte[] { 42 }, 0, segment, ValueLayout.JAVA_BYTE, 0, 1);
        MemorySegment.copy(new char[] { 42 }, 0, segment, JAVA_CHAR_ALIGNED, 0, 1);
        MemorySegment.copy(new short[] { 42 }, 0, segment, JAVA_SHORT_ALIGNED, 0, 1);
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new int[] { 42 }, 0, segment, JAVA_INT_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new float[] { 42 }, 0, segment, JAVA_FLOAT_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new long[] { 42 }, 0, segment, JAVA_LONG_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new double[] { 42 }, 0, segment, JAVA_DOUBLE_ALIGNED, 0, 1));
    }

    @Test
    public void testCopyIntoIntArray() {
        var segment = MemorySegment.ofArray(new int[2]);
        MemorySegment.copy(new byte[] { 42 }, 0, segment, ValueLayout.JAVA_BYTE, 0, 1);
        MemorySegment.copy(new char[] { 42 }, 0, segment, JAVA_CHAR_ALIGNED, 0, 1);
        MemorySegment.copy(new short[] { 42 }, 0, segment, JAVA_SHORT_ALIGNED, 0, 1);
        MemorySegment.copy(new int[] { 42 }, 0, segment, JAVA_INT_ALIGNED, 0, 1);
        MemorySegment.copy(new float[] { 42 }, 0, segment, JAVA_FLOAT_ALIGNED, 0, 1);
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new long[] { 42 }, 0, segment, JAVA_LONG_ALIGNED, 0, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> MemorySegment.copy(new double[] { 42 }, 0, segment, JAVA_DOUBLE_ALIGNED, 0, 1));
    }

    @Test
    public void testCopyIntoLongArray() {
        var segment = MemorySegment.ofArray(new long[1]);
        MemorySegment.copy(new byte[] { 42 }, 0, segment, ValueLayout.JAVA_BYTE, 0, 1);
        MemorySegment.copy(new char[] { 42 }, 0, segment, JAVA_CHAR_ALIGNED, 0, 1);
        MemorySegment.copy(new short[] { 42 }, 0, segment, JAVA_SHORT_ALIGNED, 0, 1);
        MemorySegment.copy(new int[] { 42 }, 0, segment, JAVA_INT_ALIGNED, 0, 1);
        MemorySegment.copy(new float[] { 42 }, 0, segment, JAVA_FLOAT_ALIGNED, 0, 1);
        MemorySegment.copy(new long[] { 42 }, 0, segment, JAVA_LONG_ALIGNED, 0, 1);
        MemorySegment.copy(new double[] { 42 }, 0, segment, JAVA_DOUBLE_ALIGNED, 0, 1);
    }
}
