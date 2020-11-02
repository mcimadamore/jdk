package java.nio;

import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.VM;
import jdk.internal.ref.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.FileDescriptor;
import java.util.Objects;

class DirectByteBuffer extends MappedByteBuffer implements DirectBuffer {

    Cleaner cleaner;

    public DirectByteBuffer(Deallocator deallocator, long addr, int mark, int pos, int lim, int cap, FileDescriptor fd, boolean isSync, boolean readOnly, ByteOrder order, Object attachment, MemorySegmentProxy segment) {
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
    public ByteBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        return new DirectByteBuffer(null, address + position(), markValue(), 0, rem, rem, fd, isSync, readOnly, ByteOrder.BIG_ENDIAN, attachmentValue(), segment);
    }

    @Override
    public ByteBuffer slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        return new DirectByteBuffer(null, address + index, markValue(), 0, length, length, fd, isSync, readOnly, ByteOrder.BIG_ENDIAN, attachmentValue(), segment);
    }

    @Override
    public ByteBuffer asReadOnlyBuffer() {
        return new DirectByteBuffer(null, address, markValue(), position(), limit(), capacity(), fd, isSync,
                true, ByteOrder.BIG_ENDIAN, attachmentValue(), segment);
    }

    @Override
    public ByteBuffer duplicate() {
        return new DirectByteBuffer(null, address, markValue(), position(), limit(), capacity(), fd, isSync, readOnly, ByteOrder.BIG_ENDIAN, attachmentValue(), segment);
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

    @Override
    public CharBuffer asCharBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);
        int size = rem >> 1;
        return new CharBuffer.DirectCharBuffer(address + off, -1, 0, size, size, readOnly, order, attachmentValue(), segment);
    }

    @Override
    public ShortBuffer asShortBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);
        int size = rem >> 1;
        return new ShortBuffer.DirectShortBuffer(address + off, -1, 0, size, size, readOnly, order, attachmentValue(), segment);
    }

    @Override
    public IntBuffer asIntBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);
        int size = rem >> 2;
        return new IntBuffer.DirectIntBuffer(address + off,  -1, 0, size, size, readOnly, order, attachmentValue(), segment);
    }

    @Override
    public FloatBuffer asFloatBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);
        int size = rem >> 2;
        return new FloatBuffer.DirectFloatBuffer(address + off, -1, 0, size, size, readOnly, order, attachmentValue(), segment);
    }

    @Override
    public LongBuffer asLongBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);
        int size = rem >> 3;
        return new LongBuffer.DirectLongBuffer(address + off, -1, 0, size, size, readOnly, order, attachmentValue(), segment);
    }

    @Override
    public DoubleBuffer asDoubleBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);
        int size = rem >> 3;
        return new DoubleBuffer.DirectDoubleBuffer(address + off, -1, 0, size, size, readOnly, order, attachmentValue(), segment);
    }
}
