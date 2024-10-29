/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign;

import java.lang.foreign.*;
import java.lang.foreign.MemorySegment.Scope;
import java.nio.charset.Charset;
import java.util.Objects;

public final class ArenaImpl implements Arena {

    private final MemorySessionImpl session;
    ArenaImpl(MemorySessionImpl session) {
        this.session = session;
    }

    @Override
    public Scope scope() {
        return session;
    }

    @Override
    public void close() {
        session.close();
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return session.allocate(byteSize, byteAlignment);
    }

    @Override
    public MemorySegment allocateFrom(String str) {
        return session.allocateFrom(str);
    }

    @Override
    public MemorySegment allocateFrom(String str, Charset charset) {
        return session.allocateFrom(str, charset);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfByte layout, byte value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfChar layout, char value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfShort layout, short value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfInt layout, int value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfFloat layout, float value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfLong layout, long value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfDouble layout, double value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(AddressLayout layout, MemorySegment value) {
        return session.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout elementLayout, MemorySegment source, ValueLayout sourceElementLayout, long sourceOffset, long elementCount) {
        return session.allocateFrom(elementLayout, source, sourceElementLayout, sourceOffset, elementCount);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfByte elementLayout, byte... elements) {
        return session.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfShort elementLayout, short... elements) {
        return session.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfChar elementLayout, char... elements) {
        return session.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfInt elementLayout, int... elements) {
        return session.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfFloat elementLayout, float... elements) {
        return session.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfLong elementLayout, long... elements) {
        return session.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfDouble elementLayout, double... elements) {
        return session.allocateFrom(elementLayout, elements);
    }
}
