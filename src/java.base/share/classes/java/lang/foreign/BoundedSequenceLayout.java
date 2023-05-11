package java.lang.foreign;

import jdk.internal.foreign.layout.BoundedSequenceLayoutImpl;

/**
 * A bounded sequence layout.
 */
public sealed interface BoundedSequenceLayout extends SequenceLayout permits BoundedSequenceLayoutImpl {

    /**
     * {@return the element count of this sequence layout}
     */
    long elementCount();

    /**
     * Re-arrange the elements in this sequence layout into a multi-dimensional sequence layout.
     * The resulting layout is a sequence layout where element layouts in the flattened projection of this
     * sequence layout (see {@link #flatten()}) are re-arranged into one or more nested sequence layouts
     * according to the provided element counts. This transformation preserves the layout size;
     * that is, multiplying the provided element counts must yield the same element count
     * as the flattened projection of this sequence layout.
     * <p>
     * For instance, given a sequence layout of the kind:
     * {@snippet lang=java :
     * var seq = MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT));
     * }
     * calling {@code seq.reshape(2, 6)} will yield the following sequence layout:
     * {@snippet lang=java :
     * var reshapeSeq = MemoryLayout.sequenceLayout(2, MemoryLayout.sequenceLayout(6, ValueLayout.JAVA_INT));
     * }
     * <p>
     * If one of the provided element count is the special value {@code -1}, then the element
     * count in that position will be inferred from the remaining element counts and the
     * element count of the flattened projection of this layout. For instance, a layout equivalent to
     * the above {@code reshapeSeq} can also be computed in the following ways:
     * {@snippet lang=java :
     * var reshapeSeqImplicit1 = seq.reshape(-1, 6);
     * var reshapeSeqImplicit2 = seq.reshape(2, -1);
     * }
     * @param elementCounts an array of element counts, of which at most one can be {@code -1}.
     * @return a sequence layout where element layouts in the flattened projection of this
     * sequence layout (see {@link #flatten()}) are re-arranged into one or more nested sequence layouts.
     * @throws IllegalArgumentException if two or more element counts are set to {@code -1}, or if one
     * or more element count is {@code <= 0} (but other than {@code -1}) or, if, after any required inference,
     * multiplying the element counts does not yield the same element count as the flattened projection of this
     * sequence layout.
     */
    BoundedSequenceLayout reshape(long... elementCounts);

    /**
     * Returns a flattened sequence layout. The element layout of the returned sequence layout
     * is the first non-sequence element layout found by recursively traversing the element layouts of this sequence layout.
     * This transformation preserves the layout size; nested sequence layout in this sequence layout will
     * be dropped and their element counts will be incorporated into that of the returned sequence layout.
     * For instance, given a sequence layout of the kind:
     * {@snippet lang=java :
     * var seq = MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT));
     * }
     * calling {@code seq.flatten()} will yield the following sequence layout:
     * {@snippet lang=java :
     * var flattenedSeq = MemoryLayout.sequenceLayout(12, ValueLayout.JAVA_INT);
     * }
     * @return a sequence layout with the same size as this layout (but, possibly, with different
     * element count), whose element layout is not a sequence layout.
     */
    BoundedSequenceLayout flatten();

    /**
     * {@inheritDoc}
     */
    @Override
    BoundedSequenceLayout withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    BoundedSequenceLayout withoutName();

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IllegalArgumentException if {@code bitAlignment < elementLayout().bitAlignment()}.
     */
    BoundedSequenceLayout withBitAlignment(long bitAlignment);
}
