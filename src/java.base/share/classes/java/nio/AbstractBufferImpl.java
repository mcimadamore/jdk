package java.nio;

import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.Unsafe;
import jdk.internal.ref.Cleaner;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.util.Objects;

abstract class AbstractBufferImpl<B extends AbstractBufferImpl<B, A>, A> extends Buffer {

    // Cached unsafe-access object
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final Object attachment;

    AbstractBufferImpl(long addr, Object hb, int mark, int pos, int lim, int cap,
               boolean readOnly, ByteOrder order, Object attachment, MemorySegmentProxy segment) {
        super(addr, hb, mark, pos, lim, cap, readOnly, order, segment);
        this.attachment = attachment;
    }

    /**
     * A scale factor, expressed in number of shift positions associated with the element size, which is
     * used to turn a logical buffer index into a concrete address (usable from unsafe access). That is,
     * for an int buffer, given that each buffer index covers 4 bytes, we need to shift the index
     * by 2 in order to obtain the address offset corresponding to it. For performance reasons, it is best
     * to override this method in each subclass, so that the scale factor becomes effectively a constant.
     */
    abstract int scaleFactor();

    /**
     * The base object for the unsafe access. Must be overridden by concrete subclasses so that
     * (i) cases where base == null are explicit in the code and (ii) cases where base != null also
     * feature a cast to the correct array type. Unsafe access intrinsics can work optimally
     * only if both conditions are met.
     */
    abstract Object base();

    /**
     * The array carrier associated with this buffer; e.g. a ShortBuffer will have a short[] carrier.
     */
    abstract Class<A> carrier();

    /**
     * Create a new buffer of the same type as this one, with given properties. This method is used to implement
     * various methods featuring covariant override.
     */
    abstract B dup(int offset, int mark, int pos, int lim, int cap, boolean readOnly);

    // access primitives

    final long ix(int pos) {
        return address + (pos << scaleFactor());
    }

    byte getByteInternal(int offset) {
        return UNSAFE.getByte(base(), ix(offset));
    }

    void putByteInternal(int offset, byte b) {
        UNSAFE.putByte(base(), ix(offset), b);
    }

    short getShortInternal(int offset) {
        return swap(UNSAFE.getShort(base(), ix(offset)));
    }

    void putShortInternal(int offset, short b) {
        UNSAFE.putShort(base(), ix(offset), swap(b));
    }

    char getCharInternal(int offset) {
        return swap(UNSAFE.getChar(base(), ix(offset)));
    }

    void putCharInternal(int offset, char b) {
        UNSAFE.putChar(base(), ix(offset), swap(b));
    }

    int getIntInternal(int offset) {
        return swap(UNSAFE.getIntUnaligned(base(), ix(offset)));
    }

    void putIntInternal(int offset, int i) {
        UNSAFE.putIntUnaligned(base(), ix(offset), swap(i));
    }

    float getFloatInternal(int offset) {
        return swap(UNSAFE.getFloat(base(), ix(offset)));
    }

    void putFloatInternal(int offset, float b) {
        UNSAFE.putFloat(base(), ix(offset), swap(b));
    }

    long getLongInternal(int offset) {
        return swap(UNSAFE.getLong(base(), ix(offset)));
    }

    void putLongInternal(int offset, long b) {
        UNSAFE.putLong(base(), ix(offset), swap(b));
    }

    double getDoubleInternal(int offset) {
        return swap(UNSAFE.getDouble(base(), ix(offset)));
    }

    void putDoubleInternal(int offset, double b) {
        UNSAFE.putDouble(base(), ix(offset), swap(b));
    }

    @ForceInline
    short swap(short x) {
        return order == ByteOrder.nativeOrder() ? x : Short.reverseBytes(x);
    }

    @ForceInline
    char swap(char x) {
        return order == ByteOrder.nativeOrder() ? x : Character.reverseBytes(x);
    }

    @ForceInline
    int swap(int x) {
        return order == ByteOrder.nativeOrder() ? x : Integer.reverseBytes(x);
    }

    @ForceInline
    float swap(float x) {
        int xs = swap(Float.floatToRawIntBits(x));
        return Float.intBitsToFloat(xs);
    }

    @ForceInline
    long swap(long x) {
        return order == ByteOrder.nativeOrder() ? x : Long.reverseBytes(x);
    }

    @ForceInline
    double swap(double x) {
        long xs = swap(Double.doubleToRawLongBits(x));
        return Double.longBitsToDouble(xs);
    }

    // bulk access

    final int length(A a) {
        return Array.getLength(a);
    }

    @SuppressWarnings("unchecked")
    public B put(A src, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, length(src));
        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = lim - pos;
        if (length > rem)
            throw new BufferOverflowException();

        try {
            return putInternal(pos, src, offset, length);
        } finally {
            position(pos + length);
        }
    }

    @SuppressWarnings("unchecked")
    public B put(int index, A src, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, length(src));

        return putInternal(index, src, offset, length);
    }

    @SuppressWarnings("unchecked")
    B putInternal(int index, A src, int offset, int length) {
        if (isReadOnly())
            throw new ReadOnlyBufferException();

        long srcOffset = Unsafe.getUnsafe().arrayBaseOffset(carrier()) +
                ((long)offset << scaleFactor());
        try {
            if (scaleFactor() > 0 && order != ByteOrder.nativeOrder())
                UNSAFE.copySwapMemory(src,
                        srcOffset,
                        base(),
                        ix(index),
                        (long)length << scaleFactor(),
                        (long)1 << scaleFactor());
            else
                UNSAFE.copyMemory(src,
                        srcOffset,
                        base(),
                        ix(index),
                        (long)length << scaleFactor());
        } finally {
            Reference.reachabilityFence(this);
        }
        return (B)this;
    }

    @SuppressWarnings("unchecked")
    public B get(A dst, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, length(dst));
        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = lim - pos;
        if (length > rem)
            throw new BufferUnderflowException();

        try {
            return getInternal(pos, dst, offset, length);
        } finally {
            position(pos + length);
        }
    }

    @SuppressWarnings("unchecked")
    public B get(int index, A dst, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, length(dst));

        return getInternal(index, dst, offset, length);
    }

    @SuppressWarnings("unchecked")
    B getInternal(int index, A dst, int offset, int length) {
        long dstOffset = Unsafe.getUnsafe().arrayBaseOffset(carrier()) +
                ((long)offset << scaleFactor());
        try {
            if (scaleFactor() > 0 && order != ByteOrder.nativeOrder())
                UNSAFE.copySwapMemory(base(),
                        ix(index),
                        dst,
                        dstOffset,
                        (long)length << scaleFactor(),
                        (long)1 << scaleFactor());
            else
                UNSAFE.copyMemory(base(),
                        ix(index),
                        dst,
                        dstOffset,
                        (long)length << scaleFactor());
        } finally {
            Reference.reachabilityFence(this);
        }
        return (B)this;
    }

    @SuppressWarnings("unchecked")
    public B put(B src) {
        if (src == this)
            throw createSameBufferException();
        if (isReadOnly())
            throw new ReadOnlyBufferException();

        int srcPos = src.position();
        int n = src.limit() - srcPos;
        int pos = position();
        if (n > limit() - pos)
            throw new BufferOverflowException();

        if (scaleFactor() == 0 || order == src.order) {

            try {
                UNSAFE.copyMemory(src.base(),
                        src.ix(srcPos),
                        base(),
                        ix(pos),
                        (long)n << scaleFactor());
            } finally {
                Reference.reachabilityFence(src);
                Reference.reachabilityFence(this);
            }

        } else {
            try {
                UNSAFE.copySwapMemory(src.base(),
                        src.ix(srcPos),
                        base(),
                        ix(pos),
                        (long)n << scaleFactor(),
                        (long)1 << scaleFactor());
            } finally {
                Reference.reachabilityFence(src);
                Reference.reachabilityFence(this);
            }
        }

        position(pos + n);
        src.position(srcPos + n);
        return (B)this;
    }

    // other

    B asReadOnlyBuffer() {
        return dup(0, markValue(), position(), limit(), capacity(), true);
    }

    @Override
    public B slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        int off = (pos << scaleFactor());
        return dup(off, -1, 0, rem, rem, readOnly);
    }

    @Override
    public B slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        int off = (index << scaleFactor());
        return dup(off, -1, 0, length, length, readOnly);
    }

    @Override
    public B duplicate() {
        return dup(0, markValue(), position(), limit(), capacity(), readOnly);
    }

    @SuppressWarnings("unchecked")
    public B compact() {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
        int pos = position();
        int rem = limit() - pos;
        UNSAFE.copyMemory(base(), ix(pos), base(), ix(0), rem << scaleFactor());
        position(rem);
        limit(capacity());
        discardMark();
        return (B)this;
    }

    public boolean hasArray() {
        return hb != null && hb.getClass() == carrier() && !readOnly;
    }

    @SuppressWarnings("unchecked")
    public A array() {
        if (hb == null || hb.getClass() != carrier())
            throw new UnsupportedOperationException();
        if (readOnly)
            throw new ReadOnlyBufferException();
        return (A)hb;
    }

    public int arrayOffset() {
        if (hb == null || hb.getClass() != carrier())
            throw new UnsupportedOperationException();
        if (readOnly)
            throw new ReadOnlyBufferException();
        return ((int)address - UNSAFE.arrayBaseOffset(carrier())) >> scaleFactor();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getClass().getName());
        sb.append("[pos=");
        sb.append(position());
        sb.append(" lim=");
        sb.append(limit());
        sb.append(" cap=");
        sb.append(capacity());
        sb.append("]");
        return sb.toString();
    }

    /**
     * This functional interface features a method that is used to compute the hash of a buffer element
     * at a given position.
     */
    interface BufferHashOp<B extends AbstractBufferImpl<B, A>, A> {
        int hash(B b, int index);
    }

    @SuppressWarnings("unchecked")
    public int hashCode(BufferHashOp<B, A> bufferHashOp) {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--)
            h = 31 * h + bufferHashOp.hash((B)this, i);
        return h;
    }

    /**
     * This functional interface features a method that is used to compute mismatch between two byte buffer regions,
     * starting at given positions and with given length.
     */
    interface BufferMismatchOp<B extends AbstractBufferImpl<B, A>, A> {
        int mismatch(B srcBuf, int srcPos, B dstBuf, int dstPos, int nelems);
    }

    @SuppressWarnings("unchecked")
    public boolean equals(Object ob, BufferMismatchOp<B, A> baBufferMismatchOp) {
        if (this == ob)
            return true;
        if (!(ob instanceof AbstractBufferImpl) || ((AbstractBufferImpl<?, ?>)ob).carrier() != carrier())
            return false;
        Buffer that = (Buffer)ob;
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        if (thisRem < 0 || thisRem != thatRem)
            return false;
        return baBufferMismatchOp.mismatch((B)this, thisPos, (B)that, thatPos, thisRem) < 0;
    }

    @SuppressWarnings("unchecked")
    public int mismatch(B that, BufferMismatchOp<B, A> baBufferMismatchOp) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int r = baBufferMismatchOp.mismatch((B)this, thisPos,
                that, thatPos,
                length);
        return (r == -1 && thisRem != thatRem) ? length : r;
    }

    /**
     * This functional interface features a method that is used to compare two buffer elements at given positions.
     */
    interface BufferComparatorOp<B extends AbstractBufferImpl<B, A>, A> {
        int compare(B srcBuf, int srcPos, B dstBuf, int dstPos);
    }

    @SuppressWarnings("unchecked")
    public int compareTo(B that, BufferMismatchOp<B, A> baBufferMismatchOp, BufferComparatorOp<B, A> baBufferComparatorOp) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int i = baBufferMismatchOp.mismatch((B)this, thisPos,
                that, thatPos,
                length);
        if (i >= 0) {
            return baBufferComparatorOp.compare((B)this, thisPos + i, that, thatPos + i);
        }
        return thisRem - thatRem;
    }

    // covariant overrides

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public B position(int newPosition) {
        super.position(newPosition);
        return (B)this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public B limit(int newLimit) {
        super.limit(newLimit);
        return (B)this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public B mark() {
        super.mark();
        return (B)this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public B reset() {
        super.reset();
        return (B)this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public B clear() {
        super.clear();
        return (B)this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public B flip() {
        super.flip();
        return (B)this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public B rewind() {
        super.rewind();
        return (B)this;
    }

    // direct buffer default impl - good in most cases

    public long address() {
        return address;
    }

    public Cleaner cleaner() {
        return null;
    }

    public Object attachment() {
        return attachment;
    }

    final Object attachmentValue() {
        return attachment != null ?
                attachment : this;
    }
}
