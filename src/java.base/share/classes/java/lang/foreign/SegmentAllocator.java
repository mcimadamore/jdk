/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.SlicingAllocator;
import jdk.internal.foreign.Utils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.ref.CleanerFactory;

/**
 * An object that may be used to allocate {@linkplain MemorySegment memory segments}. Clients implementing this interface
 * must implement the {@link #allocate(long, long)} method. This interface defines several default methods
 * which can be useful to create segments from several kinds of Java values such as primitives and arrays.
 * <p>
 * Passing a segment allocator to an API can be especially useful in circumstances where a client wants to communicate <em>where</em>
 * the results of a certain operation (performed by the API) should be stored, as a memory segment. For instance,
 * {@linkplain Linker#downcallHandle(FunctionDescriptor, Linker.Option...) downcall method handles} can accept an additional
 * {@link SegmentAllocator} parameter if the underlying foreign function is known to return a struct by-value. Effectively,
 * the allocator parameter tells the linker runtime where to store the return value of the foreign function.
 *
 * <h2>Native allocators</h2>
 *
 * A native allocator determines the lifetime of the native segments allocated by it. Moreover,
 * a native allocator also determines whether access to memory segments allocated by it should be
 * <a href="Arena.html#thread-confinement">restricted to specific threads</a>.
 * <p>
 * The simplest native allocator is the {@linkplain SegmentAllocator#global() global allocator}. Native segments
 * allocated with the global allocator are always accessible and their backing regions of memory are never deallocated.
 * Moreover, memory segments allocated with the global scope can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed}
 * from any thread.
 * {@snippet lang = java:
 * MemorySegment segment = SegmentAllocator.global().allocate(100);
 * ...
 * // segment is never deallocated!
 *}
 * <p>
 * Alternatively, clients can obtain an {@linkplain SegmentAllocator#auto() automatic allocator}, that is an allocator
 * whose allocated segments are managed, automatically, by the garbage collector. The regions of memory backing memory
 * segments allocated with the automatic allocator are deallocated at some unspecified time <em>after</em> they become
 * <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>, as shown below:
 *
 * {@snippet lang = java:
 * MemorySegment segment = SegmentAllocator.auto().allocate(100);
 * ...
 * segment = null; // the segment region becomes available for deallocation after this point
 *}
 * Memory segments allocated with the automatic allocator can also be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} from any thread.
 * <p>
 * Finally, clients can use an {@linkplain Arena arena}. The regions of memory
 * backing memory segments allocated with an arena are deallocated when the arena is {@linkplain Arena#close() closed}.
 * When this happens, all the segments allocated with the arena become not {@linkplain MemorySegment#isAlive() alive},
 * and subsequent access operations on these segments will fail {@link IllegalStateException}.
 *
 * {@snippet lang = java:
 * MemorySegment segment = null;
 * try (Arena arena = Arena.openConfined()) {
 *     segment = arena.scope().allocate((MemoryLayout)100);
 *     ...
 * } // segment region deallocated here
 * segment.get(ValueLayout.JAVA_BYTE, 0); // throws IllegalStateException
 *}
 *
 * Which threads can {@link MemorySegment#isAccessibleBy(Thread) access} memory segments allocated with an arena scope depends
 * on the arena kind. For instance, segments allocated with a {@linkplain Arena#openConfined() confined arena}
 * can only be accessed by the thread that created the arena. Conversely, segments allocated with a
 * {@linkplain Arena#openConfined() shared arena} can be accessed by any thread.
 *
 * <h2>Derived allocators</h2>
 *
 * This interface also defines factories for commonly used allocators:
 * <ul>
 *     <li>{@link #slicingAllocator(MemorySegment)} obtains an efficient slicing allocator, where memory
 *     is allocated by repeatedly slicing the provided memory segment;</li>
 *     <li>{@link #prefixAllocator(MemorySegment)} obtains an allocator which wraps a segment (either on-heap or off-heap)
 *     and recycles its content upon each new allocation request.</li>
 *     <li>{@link #coallocator(MemorySegment)} obtains an allocator which allocates new native segments which feature
 *     the same lifetime as that of the provided segment.</li>
 * </ul>
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public interface SegmentAllocator {

    /**
     * Obtains the automatic allocator. Segments allocated with the automatic allocator are managed, implicitly,
     * by the garbage collector, and can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread.
     *
     * @return the automatic allocator.
     */
    static SegmentAllocator auto() {
        class Holder {
            static SegmentAllocator AUTO_ALLOCATOR = new SegmentAllocator() {
                @Override
                public MemorySegment allocate(long byteSize, long byteAlignment) {
                    return MemorySessionImpl.createImplicit(CleanerFactory.cleaner()).allocate(byteSize, byteAlignment);
                }

                @Override
                public MemorySegment wrap(long address, Runnable cleanupAction) {
                    return MemorySessionImpl.createImplicit(CleanerFactory.cleaner()).wrap(address, cleanupAction);
                }
            };
        }
        return Holder.AUTO_ALLOCATOR;
    }

    /**
     * Obtains the global allocator. Segments allocated are always {@link MemorySegment#isAlive()} and can be
     * {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread.
     *
     * @return the global scope.
     */
    static SegmentAllocator global() {
        class Holder {
            static SegmentAllocator GLOBAL_ALLOCATOR = new SegmentAllocator() {
                @Override
                public MemorySegment allocate(long byteSize, long byteAlignment) {
                    return MemorySessionImpl.GLOBAL.allocate(byteSize, byteAlignment);
                }

                @Override
                public MemorySegment wrap(long address, Runnable cleanupAction) {
                    return MemorySessionImpl.GLOBAL.wrap(address, cleanupAction);
                }
            };
        }
        return Holder.GLOBAL_ALLOCATOR;
    }

    /**
     * Converts a Java string into a UTF-8 encoded, null-terminated C string,
     * storing the result into a memory segment.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     * <p>
     * If the given string contains any {@code '\0'} characters, they will be
     * copied as well. This means that, depending on the method used to read
     * the string, such as {@link MemorySegment#getUtf8String(long)}, the string
     * will appear truncated when read again.
     *
     * @implSpec the default implementation for this method copies the contents of the provided Java string
     * into a new memory segment obtained by calling {@code this.allocate(str.length() + 1)}.
     * @param str the Java string to be converted into a C string.
     * @return a new native segment containing the converted C string.
     */
    default MemorySegment allocateUtf8String(String str) {
        Objects.requireNonNull(str);
        return Utils.toCString(str.getBytes(StandardCharsets.UTF_8), this);
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given byte value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfByte layout, byte value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given char value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfChar layout, char value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given short value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfShort layout, short value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given int value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfInt layout, int value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given float value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfFloat layout, float value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given long value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfLong layout, long value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given double value.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfDouble layout, double value) {
        Objects.requireNonNull(layout);
        VarHandle handle = layout.varHandle();
        MemorySegment addr = allocate(layout);
        handle.set(addr, value);
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given address value.
     * The address value might be narrowed according to the platform address size (see {@link ValueLayout#ADDRESS}).
     * @implSpec the default implementation for this method calls {@code this.allocate(layout)}.
     * @param layout the layout of the block of memory to be allocated.
     * @param value the value to be set on the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(ValueLayout.OfAddress layout, MemorySegment value) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(layout);
        MemorySegment segment = allocate(layout);
        layout.varHandle().set(segment, value);
        return segment;
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given byte elements.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements the byte elements to be copied to the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfByte elementLayout, byte... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given short elements.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements the short elements to be copied to the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfShort elementLayout, short... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given char elements.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements the char elements to be copied to the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfChar elementLayout, char... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given int elements.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements the int elements to be copied to the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfInt elementLayout, int... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given float elements.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements the float elements to be copied to the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfFloat elementLayout, float... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given long elements.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements the long elements to be copied to the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfLong elementLayout, long... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    /**
     * Allocates a memory segment with the given layout and initializes it with the given double elements.
     * @implSpec the default implementation for this method calls {@code this.allocateArray(layout, array.length)}.
     * @param elementLayout the element layout of the array to be allocated.
     * @param elements the double elements to be copied to the newly allocated memory block.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocateArray(ValueLayout.OfDouble elementLayout, double... elements) {
        return copyArrayWithSwapIfNeeded(elements, elementLayout, MemorySegment::ofArray);
    }

    private <Z> MemorySegment copyArrayWithSwapIfNeeded(Z array, ValueLayout elementLayout,
                                                        Function<Z, MemorySegment> heapSegmentFactory) {
        int size = Array.getLength(Objects.requireNonNull(array));
        MemorySegment addr = allocateArray(Objects.requireNonNull(elementLayout), size);
        if (size > 0) {
            MemorySegment.copy(heapSegmentFactory.apply(array), elementLayout, 0,
                    addr, elementLayout.withOrder(ByteOrder.nativeOrder()), 0, size);
        }
        return addr;
    }

    /**
     * Allocates a memory segment with the given layout.
     * @implSpec the default implementation for this method calls {@code this.allocate(layout.byteSize(), layout.byteAlignment())}.
     * @param layout the layout of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     */
    default MemorySegment allocate(MemoryLayout layout) {
        Objects.requireNonNull(layout);
        return allocate(layout.byteSize(), layout.byteAlignment());
    }

    /**
     * Allocates a memory segment with the given element layout and size.
     * @implSpec the default implementation for this method calls {@code this.allocate(MemoryLayout.sequenceLayout(count, elementLayout))}.
     * @param elementLayout the array element layout.
     * @param count the array element count.
     * @return a segment for the newly allocated memory block.
     * @throws IllegalArgumentException if {@code count < 0}.
     */
    default MemorySegment allocateArray(MemoryLayout elementLayout, long count) {
        Objects.requireNonNull(elementLayout);
        if (count < 0) {
            throw new IllegalArgumentException("Negative array size");
        }
        return allocate(MemoryLayout.sequenceLayout(count, elementLayout));
    }

    /**
     * Allocates a memory segment with the given size.
     * @implSpec the default implementation for this method calls {@code this.allocate(byteSize, 1)}.
     * @param byteSize the size (in bytes) of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     * @throws IllegalArgumentException if {@code byteSize < 0}
     */
    default MemorySegment allocate(long byteSize) {
        return allocate(byteSize, 1);
    }

    /**
     * Allocates a memory segment with the given size and alignment constraint.
     * @param byteSize the size (in bytes) of the block of memory to be allocated.
     * @param byteAlignment the alignment (in bytes) of the block of memory to be allocated.
     * @return a segment for the newly allocated memory block.
     * @throws IllegalArgumentException if {@code byteSize < 0}, {@code byteAlignment <= 0},
     * or if {@code alignmentBytes} is not a power of 2.
     */
    MemorySegment allocate(long byteSize, long byteAlignment);

    /**
     * Returns a segment allocator which responds to allocation requests by returning consecutive slices
     * obtained from the provided segment. Each new allocation request will return a new slice starting at the
     * current offset (modulo additional padding to satisfy alignment constraint), with given size.
     * <p>
     * When the returned allocator cannot satisfy an allocation request, e.g. because a slice of the provided
     * segment with the requested size cannot be found, an {@link IndexOutOfBoundsException} is thrown.
     *
     * @param segment the segment which the returned allocator should slice from.
     * @return a new slicing allocator
     */
    static SegmentAllocator slicingAllocator(MemorySegment segment) {
        Objects.requireNonNull(segment);
        return new SlicingAllocator(segment);
    }

    /**
     * Returns a segment allocator which responds to allocation requests by recycling a single segment. Each
     * new allocation request will return a new slice starting at the segment offset {@code 0} (alignment
     * constraints are ignored by this allocator), hence the name <em>prefix allocator</em>.
     * Equivalent to (but likely more efficient than) the following code:
     * {@snippet lang=java :
     * MemorySegment segment = ...
     * SegmentAllocator prefixAllocator = (size, align) -> segment.asSlice(0, size);
     * }
     * <p>
     * This allocator can be useful to limit allocation requests in case a client
     * knows that they have fully processed the contents of the allocated segment before the subsequent allocation request
     * takes place.
     * <p>
     * While the allocator returned by this method is <em>thread-safe</em>, concurrent access on the same recycling
     * allocator might cause a thread to overwrite contents written to the underlying segment by a different thread.
     *
     * @param segment the memory segment to be recycled by the returned allocator.
     * @return an allocator which recycles an existing segment upon each new allocation request.
     */
    static SegmentAllocator prefixAllocator(MemorySegment segment) {
        return (AbstractMemorySegmentImpl)Objects.requireNonNull(segment);
    }

    /**
     * Allocates in the same lifetime as given segment.
     * @param segment segment.
     * @return an allocator which allocates in the same lifetime as given segment.
     */
    static SegmentAllocator coallocator(MemorySegment segment) {
        return ((AbstractMemorySegmentImpl)segment).sessionImpl();
    }

    /**
     * Creates a native segment with the given address, and cleanup action.
     * This method can be useful when interacting with custom memory sources (e.g. custom allocators),
     * where an address to some underlying region of memory is typically obtained from foreign code
     * (often as a plain {@code long} value).
     * <p>
     * The returned segment is not read-only (see {@link MemorySegment#isReadOnly()}), and is associated with the
     * provided scope.
     * <p>
     * The provided cleanup action (if any) will be invoked when the returned segment becomes not {@linkplain MemorySegment#isAlive() alive}.
     * <p>
     * Clients should ensure that the address and bounds refer to a valid region of memory that is accessible for reading and,
     * if appropriate, writing; an attempt to access an invalid address from Java code will either return an arbitrary value,
     * have no visible effect, or cause an unspecified exception to be thrown.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     *
     * @param address the returned segment's address.
     * @param cleanupAction the custom cleanup action to be associated to the returned segment (can be null).
     * @return a native segment with the given address, size and scope.
     * @throws IllegalArgumentException if {@code byteSize < 0}.
     * @throws IllegalStateException if {@code scope} is an already closed {@link Arena}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is specified, but does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    MemorySegment wrap(long address, Runnable cleanupAction);
}
