/*
 * Copyright (c) 2000, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.nio;

import jdk.internal.access.foreign.MemorySegmentProxy;

import java.util.Objects;

// ## If the sequence is a string, use reflection to share its array

class StringCharBuffer                                  // package-private
    extends CharBuffer
{
    CharSequence str;

    StringCharBuffer(CharSequence s, int start, int end) { // package-private
        super(0, null, -1, start, end, s.length(), true, ByteOrder.nativeOrder(), null, null);
        int n = s.length();
        Objects.checkFromToIndex(start, end, n);
        str = s;
    }

    @Override
    int scaleFactor() {
        // we need to remove any scaling factor, otherwise the slice implementation in AbstractBufferImpl
        // will create slices with the wrong starting offset. This is caused by the fact that we are reusing
        // the address field to store the offset into the char sequence. Ideally, StringCharBuffer should not
        // be a subclass of AbstractBufferImpl (although doing so allows to reuse code); that choice also leads
        // to the need to the isAddressable predicate.
        return 0;
    }

    private StringCharBuffer(CharSequence s,
                             long address,
                             int mark,
                             int pos,
                             int limit,
                             int cap) {
        super(address, null, mark, pos, limit, cap, true, ByteOrder.nativeOrder(), null, null);
        str = s;
    }

    @Override
    CharBuffer dup(int offset, int mark, int pos, int lim, int cap, boolean readOnly) {
        return new StringCharBuffer(str, address + offset, mark, pos, lim, cap);
    }

    private int stringOffset() {
        return (int)address;
    }

    public final char get() {
        return str.charAt(nextGetIndex() + stringOffset());
    }

    public final char get(int index) {
        return str.charAt(checkGetIndex(index) + stringOffset());
    }

    @Override
    public CharBuffer get(char[] dst, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, dst.length);
        if (length > remaining())
            throw new BufferUnderflowException();
        int end = offset + length;
        for (int i = offset; i < end; i++)
            dst[i] = get();
        return this;
    }

    @Override
    public CharBuffer get(int index, char[] dst, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, dst.length);
        int end = offset + length;
        for (int i = offset, j = index; i < end; i++, j++)
            dst[i] = get(j);
        return this;
    }

    final String toString(int start, int end) {
        return str.subSequence(start + stringOffset(), end + stringOffset()).toString();
    }

    boolean isAddressable() {
        return false;
    }
}
