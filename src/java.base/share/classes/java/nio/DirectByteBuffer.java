package java.nio;

import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.VM;
import jdk.internal.ref.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.FileDescriptor;
import java.util.Objects;

class DirectByteBuffer extends MappedByteBuffer implements DirectBuffer {

    Cleaner cleaner;

    public DirectByteBuffer(Deallocator deallocator, long addr, int mark, int pos, int lim, int cap, FileDescriptor fd,
                            boolean isSync, boolean readOnly, ByteOrder order, Object attachment, MemorySegmentProxy segment) {
        super(addr, mark, pos, lim, cap, fd, isSync, readOnly, order, attachment, segment);
        if (deallocator != null) {
            this.cleaner = Cleaner.create(this, deallocator);
        }
    }

    // Invoked only by JNI: NewDirectByteBuffer(void*, long)
    //
    private DirectByteBuffer(long addr, int cap) {
        super(addr, -1, 0, cap, cap, null, false, false, ByteOrder.nativeOrder(), null, null);
        cleaner = null;
    }

    // For memory-mapped buffers -- invoked by FileChannelImpl via reflection
    //
    protected DirectByteBuffer(int cap, long addr,
                               FileDescriptor fd,
                               Runnable unmapper,
                               boolean isSync, boolean readOnly, MemorySegmentProxy segment)
    {
        super(addr, -1, 0, cap, cap, fd, isSync, readOnly, ByteOrder.BIG_ENDIAN, null, segment);
        cleaner = Cleaner.create(this, unmapper);
    }

    @Override
    Object base() {
        return null;
    }

    @Override
    public long address() {
        return address;
    }

    @Override
    public Cleaner cleaner() {
        return cleaner;
    }

    @Override
    public Object attachment() {
        return attachment;
    }

    @Override
    ByteBuffer dup(int offset, int mark, int pos, int lim, int cap, boolean readOnly) {
        return new DirectByteBuffer(null, address + offset, mark, pos, lim, cap, fd, isSync,
                readOnly, ByteOrder.BIG_ENDIAN, attachmentValue(), segment);
    }

    private static class Deallocator
            implements Runnable
    {

        private long address;
        private long size;
        private int capacity;

        private Deallocator(long address, long size, int capacity) {
            assert (address != 0);
            this.address = address;
            this.size = size;
            this.capacity = capacity;
        }

        public void run() {
            if (address == 0) {
                // Paranoia
                return;
            }
            UNSAFE.freeMemory(address);
            address = 0;
            Bits.unreserveMemory(size, capacity);
        }

    }

    static MappedByteBuffer makeDirectBuffer(int cap) {
        boolean pa = VM.isDirectMemoryPageAligned();
        int ps = Bits.pageSize();
        long size = Math.max(1L, (long)cap + (pa ? ps : 0));
        Bits.reserveMemory(size, cap);

        long base = 0;
        try {
            base = UNSAFE.allocateMemory(size);
        } catch (OutOfMemoryError x) {
            Bits.unreserveMemory(size, cap);
            throw x;
        }
        UNSAFE.setMemory(base, size, (byte) 0);
        final long address;
        if (pa && (base % ps != 0)) {
            // Round up to page boundary
            address = base + ps - (base & (ps - 1));
        } else {
            address = base;
        }
        return new DirectByteBuffer(new Deallocator(base, size, cap), address, -1, 0, cap, cap, null,
                false, false, ByteOrder.BIG_ENDIAN, null, null);
    }

    @Override
    public boolean isDirect() {
        return true;
    }
}
