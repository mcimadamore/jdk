package jdk.internal.foreign;

import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

/**
 * Marker interface for allocators that provide zeroing semantics.
 */
public interface ZeroingAllocator extends SegmentAllocator {

    static ZeroingAllocator of(SegmentAllocator allocator) {
        if (allocator instanceof ZeroingAllocator zeroingAllocator) {
            // already a zeroing allocator
            return zeroingAllocator;
        } else if (allocator instanceof MemorySegment ||
                   allocator instanceof SlicingAllocator) {
            // prefix and slicing allocators do not zero
            return (byteSize, byteAlignment) -> allocator.allocate(byteSize, byteAlignment).fill((byte)0);
        } else {
            return new ZeroingWrapper(allocator);
        }
    }

    class ZeroingWrapper implements ZeroingAllocator {
        static final MemorySegment ZERO = MemorySegment.ofArray(new long[1024]);
        @Stable
        static final ValueLayout[] ALIGNED_LAYOUTS;

        final SegmentAllocator allocator;

        ZeroingWrapper(SegmentAllocator allocator) {
            this.allocator = allocator;
        }

        static {
            ALIGNED_LAYOUTS = new ValueLayout[8];
            ALIGNED_LAYOUTS[0] = ValueLayout.JAVA_BYTE;
            ALIGNED_LAYOUTS[1] = ValueLayout.JAVA_SHORT;
            ALIGNED_LAYOUTS[3] = ValueLayout.JAVA_INT;
            ALIGNED_LAYOUTS[7] = ValueLayout.JAVA_LONG;
        }

        public MemorySegment allocate(long size, long align) {
            if (align > 8 || size > ZERO.byteSize()) {
                // slow path (might perform double zeroing)
                return allocator.allocate(size, align).fill((byte) 0);
            } else {
                return allocator.allocateFrom(ALIGNED_LAYOUTS[(int)align - 1], ZERO, ALIGNED_LAYOUTS[(int)align - 1], 0, size);
            }
        };
    }
}
