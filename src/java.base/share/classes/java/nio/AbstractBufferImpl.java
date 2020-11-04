package java.nio;

import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.ref.Reference;
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

    abstract void loadAndPutAbsolute(A arr, int i, int j);

    abstract void getAbsoluteAndStore(A arr, int i, int j);

    abstract void loadAndPutRelative(A arr, int i);

    abstract void getRelativeAndStore(A arr, int i);

    abstract int length(A a);

    abstract B dup(long addr, Object hb, int mark, int pos, int lim, int cap,
                   boolean readOnly, Object attachment, MemorySegmentProxy segment);

    B asReadOnlyBuffer() {
        return dup(address, base(), markValue(), position(), limit(), capacity(),
                true, attachmentValue(), segment);
    }

    @Override
    public B slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        int off = (pos << carrierSize());
        return dup(address + off, base(), markValue(), 0, rem, rem, readOnly, attachmentValue(), segment);
    }

    @Override
    public B slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        int off = (index << carrierSize());
        return dup(address + off, base(), markValue(), 0, length, length, readOnly, attachmentValue(), segment);
    }

    @Override
    public B duplicate() {
        return dup(address, base(), markValue(), position(), limit(), capacity(), readOnly, attachmentValue(), segment);
    }

    @SuppressWarnings("unchecked")
    public B put(A src, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, length(src));
        if (length > remaining())
            throw new BufferOverflowException();
        int end = offset + length;
        for (int i = offset; i < end; i++)
            loadAndPutRelative(src, i);
        return (B)this;
    }

    @SuppressWarnings("unchecked")
    public B put(int index, A src, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, length(src));
        int end = offset + length;
        for (int i = offset, j = index; i < end; i++, j++)
            loadAndPutAbsolute(src, i, j);
        return (B)this;
    }

    @SuppressWarnings("unchecked")
    public B get(A dst, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, length(dst));
        if (length > remaining())
            throw new BufferUnderflowException();
        int end = offset + length;
        for (int i = offset; i < end; i++)
            getRelativeAndStore(dst, i);
        return (B)this;
    }

    @SuppressWarnings("unchecked")
    public B get(int index, A dst, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, length(dst));
        int end = offset + length;
        for (int i = offset, j = index; i < end; i++, j++)
            getAbsoluteAndStore(dst, i, j);
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

        Object srcBase = src.base();

        assert srcBase != null || src.isDirect();

        Object base = base();
        assert base != null || isDirect();

        long srcAddr = src.address + ((long)srcPos << carrierSize());
        long addr = address + ((long)pos << carrierSize());
        long len = (long)n << carrierSize();

        if (carrierSize() == 0 || order == src.order) {

            try {
                UNSAFE.copyMemory(srcBase,
                        srcAddr,
                        base,
                        addr,
                        len);
            } finally {
                Reference.reachabilityFence(src);
                Reference.reachabilityFence(this);
            }

        } else {
            try {
                UNSAFE.copySwapMemory(srcBase,
                        srcAddr,
                        base,
                        addr,
                        len,
                        (long)1 << carrierSize());
            } finally {
                Reference.reachabilityFence(src);
                Reference.reachabilityFence(this);
            }
        }

        position(pos + n);
        src.position(srcPos + n);
        return (B)this;
    }

    abstract int carrierSize();

    /**
     *
     * @return the base reference, paired with the address
     * field, which in combination can be used for unsafe access into a heap
     * buffer or direct byte buffer (and views of).
     */
    final Object base() {
        return hb;
    }

    // access primitives

    final long ix(int pos) {
        return address + (pos << carrierSize());
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

    // other

    Object attachmentValue() {
        if (attachment == null && isDirect()) {
            return this;
        } else {
            return attachment;
        }
    }

    abstract Class<A> carrier();

    @SuppressWarnings("unchecked")
    public B compact() {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
        int pos = position();
        int rem = limit() - pos;
        UNSAFE.copyMemory(base(), ix(pos), base(), ix(0), rem << carrierSize());
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
        return ((int)address - UNSAFE.arrayBaseOffset(carrier())) >> carrierSize();
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

    public int hashCode() {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--)
            h = 31 * h + getAsInt(i);
        return h;
    }

    abstract int getAsInt(int index);

    @SuppressWarnings("unchecked")
    public boolean equals(Object ob) {
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
        return mismatchInternal((B)this, thisPos, (B)that, thatPos, thisRem) < 0;
    }

    @SuppressWarnings("unchecked")
    public int mismatch(B that) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int r = mismatchInternal((B)this, thisPos,
                that, thatPos,
                length);
        return (r == -1 && thisRem != thatRem) ? length : r;
    }

    abstract int mismatchInternal(B src, int srcPos, B dest, int destPos, int n);

    @SuppressWarnings("unchecked")
    public int compareTo(B that) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int i = mismatchInternal((B)this, thisPos,
                that, thatPos,
                length);
        if (i >= 0) {
            return compare((B)this, thisPos + i, that, thatPos + i);
        }
        return thisRem - thatRem;
    }

    abstract int compare(B thisBuf, int thisPos, B thatBuf, int thatPos);

    // covariant overrides

    // -- Covariant return type overrides

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
}
