package java.nio;

import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.ref.Reference;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

abstract class AbstractBufferImpl<B extends AbstractBufferImpl<B, A>, A> extends Buffer {

    // Cached unsafe-access object
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Used by heap byte buffers or direct buffers with Unsafe access
    // For heap byte buffers this field will be the address relative to the
    // array base address and offset into that array. The address might
    // not align on a word boundary for slices, nor align at a long word
    // (8 byte) boundary for byte[] allocations on 32-bit systems.
    // For direct buffers it is the start address of the memory region. The
    // address might not align on a word boundary for slices, nor when created
    // using JNI, see NewDirectByteBuffer(void*, long).
    // Should ideally be declared final
    // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
    final long address;
    final Object hb;
    final Object attachment;

    AbstractBufferImpl(long addr, Object hb, int mark, int pos, int lim, int cap,
               boolean readOnly, ByteOrder order, Object attachment, MemorySegmentProxy segment) {
        super(mark, pos, lim, cap, readOnly, order, segment);
        this.address = addr;
        this.hb = hb;
        this.attachment = attachment;
    }

    abstract void getAndPut(A arr, int i, int j);

    abstract void putAndGet(A arr, int i, int j);

    abstract int length(A a);

    @SuppressWarnings("unchecked")
    public B put(int index, A src, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, length(src));
        int end = offset + length;
        for (int i = offset, j = index; i < end; i++, j++)
            getAndPut(src, i, j);
        return (B)this;
    }

    @SuppressWarnings("unchecked")
    public B get(int index, A dst, int offset, int length) {
        if (isReadOnly())
            throw new ReadOnlyBufferException();
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, length(dst));
        int end = offset + length;
        for (int i = offset, j = index; i < end; i++, j++)
            putAndGet(dst, i, j);
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

    long ix(int pos) {
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
        if (hb == null || hb.getClass() == carrier())
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
        return (int)address - UNSAFE.arrayBaseOffset(carrier());
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
            h = 31 * h + (int)getAsInt(i);
        return h;
    }

    abstract int getAsInt(int index);

    @SuppressWarnings("unchecked")
    public boolean equals(Object ob) {
        if (this == ob)
            return true;
        if (!getClass().isAssignableFrom(ob.getClass()) && !ob.getClass().isAssignableFrom(getClass()))
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
    public int mismatch(ByteBuffer that) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int r = mismatchInternal((B)this, thisPos,
                (B)that, thatPos,
                length);
        return (r == -1 && thisRem != thatRem) ? length : r;
    }

    abstract int mismatchInternal(B src, int srcPos, B dest, int destPos, int n);

    @SuppressWarnings("unchecked")
    public int compareTo(ByteBuffer that) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int i = mismatchInternal((B)this, thisPos,
                (B)that, thatPos,
                length);
        if (i >= 0) {
            return compare((B)this, thisPos + i, (B)that, thatPos + i);
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
