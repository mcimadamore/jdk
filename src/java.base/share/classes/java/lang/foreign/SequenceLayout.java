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
package java.lang.foreign;

import jdk.internal.foreign.layout.BoundedSequenceLayoutImpl;
import jdk.internal.foreign.layout.UnboundedSequenceLayoutImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A compound layout that denotes a repetition of a given <em>element layout</em>.
 * The repetition count is said to be the sequence layout's <em>element count</em>. A finite sequence can be thought of as a
 * group layout where the sequence layout's element layout is repeated a number of times that is equal to the sequence
 * layout's element count. In other words this layout:
 *
 * {@snippet lang=java :
 * MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN));
 * }
 *
 * is equivalent to the following layout:
 *
 * {@snippet lang=java :
 * MemoryLayout.structLayout(
 *     ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
 *     ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
 *     ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN));
 * }
 *
 * @implSpec
 * This class is immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface SequenceLayout extends MemoryLayout permits BoundedSequenceLayout, UnboundedSequenceLayoutImpl {

    /**
     * {@return the element layout associated with this sequence layout}
     */
    MemoryLayout elementLayout();

    /**
     * Returns a sequence layout with the same element layout, alignment constraint and name as this sequence layout,
     * but with the specified element count.
     * @param elementCount the new element count.
     * @return a sequence layout with the given element count.
     * @throws IllegalArgumentException if {@code elementCount < 0}.
     */
    BoundedSequenceLayout withElementCount(long elementCount);

    /**
     * {@inheritDoc}
     */
    @Override
    SequenceLayout withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    MemoryLayout withoutName();

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IllegalArgumentException if {@code bitAlignment < elementLayout().bitAlignment()}.
     */
    SequenceLayout withBitAlignment(long bitAlignment);
}
