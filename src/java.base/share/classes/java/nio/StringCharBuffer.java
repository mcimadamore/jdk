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

    public CharBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        return new StringCharBuffer(str,
                                    address + pos,
                                    -1,
                                    0,
                                    rem,
                                    rem);
    }

    @Override
    public CharBuffer slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        return new StringCharBuffer(str,
                                    address + index,
                                    -1,
                                    0,
                                    length,
                                    length);
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

    public CharBuffer duplicate() {
        return new StringCharBuffer(str, address, markValue(),
                                    position(), limit(), capacity());
    }

    public CharBuffer asReadOnlyBuffer() {
        return duplicate();
    }

    int stringOffset() {
        return (int)address;
    }

    public final char get() {
        return str.charAt(nextGetIndex() + stringOffset());
    }

    public final char get(int index) {
        return str.charAt(checkGetIndex(index) + stringOffset());
    }

    char getUnchecked(int index) {
        return str.charAt(index + stringOffset());
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

    @Override
    public CharBuffer put(char[] src, int offset, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public CharBuffer put(int index, char[] src, int offset, int length) {
        throw new ReadOnlyBufferException();
    }

    public final CharBuffer put(char c) {
        throw new ReadOnlyBufferException();
    }

    public final CharBuffer put(int index, char c) {
        throw new ReadOnlyBufferException();
    }

    public final CharBuffer compact() {
        throw new ReadOnlyBufferException();
    }

    public final boolean isReadOnly() {
        return true;
    }

    final String toString(int start, int end) {
        return str.subSequence(start + stringOffset(), end + stringOffset()).toString();
    }

    public final CharBuffer subSequence(int start, int end) {
        try {
            int pos = position();
            return new StringCharBuffer(str, stringOffset(),
                                        -1,
                                        pos + checkGetIndex(start, pos),
                                        pos + checkGetIndex(end, pos),
                                        capacity());
        } catch (IllegalArgumentException x) {
            throw new IndexOutOfBoundsException();
        }
    }

    public boolean isDirect() {
        return false;
    }

    public ByteOrder order() {
        return ByteOrder.nativeOrder();
    }

    ByteOrder charRegionOrder() {
        return null;
    }

    boolean isAddressable() {
        return false;
    }

    public boolean equals(Object ob) {
        if (this == ob)
            return true;
        if (!(ob instanceof CharBuffer))
            return false;
        CharBuffer that = (CharBuffer)ob;
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        if (thisRem < 0 || thisRem != thatRem)
            return false;
        return BufferMismatch.mismatch(this, thisPos,
                                       that, thatPos,
                                       thisRem) < 0;
    }

    public int compareTo(CharBuffer that) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int i = BufferMismatch.mismatch(this, thisPos,
                                        that, thatPos,
                                        length);
        if (i >= 0) {
            return Character.compare(this.get(thisPos + i), that.get(thatPos + i));
        }
        return thisRem - thatRem;
    }
}
