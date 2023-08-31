package org.openjdk.bench.java.lang.foreign.xor;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class GetArrayUnsafeXorOpImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");
    }

    static final Unsafe UNSAFE;

    //setup unsafe
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException();
        }
    }
    static final int BYTE_ARR_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    GetArrayUnsafeXorOpImpl() {
    }

    public void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable {
        long srcBuf = UNSAFE.allocateMemory(len);
        long dstBuf = UNSAFE.allocateMemory(len);
        UNSAFE.copyMemory(src, sOff + BYTE_ARR_OFFSET, null, srcBuf, len);
        UNSAFE.copyMemory(dst, dOff + BYTE_ARR_OFFSET, null, dstBuf, len);
        xorOp(srcBuf, dstBuf, len);
        UNSAFE.copyMemory(null, dstBuf, dst, dOff + BYTE_ARR_OFFSET, len);
        UNSAFE.freeMemory(srcBuf);
        UNSAFE.freeMemory(dstBuf);
    }

    native void xorOp(long src, long dst, int len);
}
