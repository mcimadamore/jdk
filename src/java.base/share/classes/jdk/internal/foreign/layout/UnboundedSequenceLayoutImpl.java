/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.foreign.layout;

import java.lang.foreign.BoundedSequenceLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.util.Objects;
import java.util.Optional;

public final class UnboundedSequenceLayoutImpl extends AbstractLayout<UnboundedSequenceLayoutImpl> implements SequenceLayout {

    private final MemoryLayout elementLayout;

    private UnboundedSequenceLayoutImpl(MemoryLayout elementLayout) {
        this(elementLayout, elementLayout.bitAlignment(), Optional.empty());
    }

    public UnboundedSequenceLayoutImpl(MemoryLayout elementLayout, long bitAlignment, Optional<String> name) {
        super(0, bitAlignment, name);
        this.elementLayout = elementLayout;
    }

    /**
     * {@return the element layout associated with this sequence layout}
     */
    public MemoryLayout elementLayout() {
        return elementLayout;
    }

    @Override
    public long bitSize() {
        throw new UnsupportedOperationException("Unbounded sequence layout");
    }

    @Override
    public long byteSize() {
        throw new UnsupportedOperationException("Unbounded sequence layout");
    }

    /**
     * Returns a sequence layout with the same element layout, alignment constraints and name as this sequence layout,
     * but with the specified element count.
     *
     * @param elementCount the new element count.
     * @return a sequence layout with the given element count.
     * @throws IllegalArgumentException if {@code elementCount < 0}.
     */
    public BoundedSequenceLayout withElementCount(long elementCount) {
        return new BoundedSequenceLayoutImpl(elementCount, elementLayout, bitAlignment(), name());
    }

    @Override
    public String toString() {
        return decorateLayoutString(String.format("[*:%s]",
                elementLayout));
    }

    @Override
    public boolean equals(Object other) {
        return this == other ||
                other instanceof UnboundedSequenceLayoutImpl otherSeq &&
                        super.equals(other) &&
                        elementLayout.equals(otherSeq.elementLayout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), elementLayout);
    }

    @Override
    UnboundedSequenceLayoutImpl dup(long bitAlignment, Optional<String> name) {
        return new UnboundedSequenceLayoutImpl(elementLayout, bitAlignment, name);
    }

    @Override
    public UnboundedSequenceLayoutImpl withBitAlignment(long bitAlignment) {
        if (bitAlignment < elementLayout.bitAlignment()) {
            throw new IllegalArgumentException("Invalid alignment constraint");
        }
        return super.withBitAlignment(bitAlignment);
    }

    @Override
    public boolean hasNaturalAlignment() {
        return bitAlignment() == elementLayout.bitAlignment();
    }

    public static SequenceLayout of(MemoryLayout elementLayout) {
        return new UnboundedSequenceLayoutImpl(MemoryLayoutUtil.requireNoUnboundedSequence(elementLayout));
    }
}
