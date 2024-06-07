package jdk.internal.foreign;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * Internal interface for allocators that provide zeroing semantics.
 */
public interface ZeroingAllocator extends SegmentAllocator {

    MemorySegment allocateRaw(long byteSize, long byteAlign);

    @Override
    default MemorySegment allocate(long byteSize, long byteAlign) {
        return allocateRaw(byteSize, byteAlign).fill((byte)0);
    }

    static ZeroingAllocator of(SegmentAllocator allocator) {
        return (allocator instanceof ZeroingAllocator zeroingAllocator) ?
                zeroingAllocator : // already a zeroing allocator
                allocator::allocate;
    }
}
