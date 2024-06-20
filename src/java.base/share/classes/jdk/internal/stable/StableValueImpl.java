package jdk.internal.stable;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.VarHandle.AccessMode;
import java.util.NoSuchElementException;
import java.util.Objects;

public non-sealed abstract class StableValueImpl<X> implements java.lang.StableValue<X> {
    @Override
    public boolean trySet(X value, int... indices) {
        return cas(defaultValue(), value, indices);
    }

    @Override
    public X orElse(X other, int... indices) {
        X x = get(indices);
        return x.equals(defaultValue()) ?
            other : x;
    }

    @Override
    public X orElseThrow(int... indices) {
        X x = get(indices);
        if (x.equals(defaultValue())) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override
    public boolean isSet(int... indices) {
        return !get(indices).equals(defaultValue());
    }

    @Override
    public Class<?> type() {
        return varHandle().varType();
    }

    public abstract X defaultValue();
    public abstract X get(int... indices);
    public abstract boolean cas(X oldValue, X newValue, int... indices);

    public static class OfInt extends StableValueImpl<Integer> {

        @Stable
        int x;

        static final VarHandle RAW_HANDLE;

        static {
            try {
                RAW_HANDLE = MethodHandles.lookup()
                        .findVarHandle(OfInt.class, "x", int.class);
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        @Override
        public Integer defaultValue() {
            return 0;
        }

        @Override
        public VarHandle varHandle() {
            return RAW_HANDLE;
        }

        @Override
        public Integer get(int... indices) {
            return (Integer)RAW_HANDLE.get(this);
        }

        @Override
        public boolean cas(Integer oldValue, Integer newValue, int... indices) {
            return RAW_HANDLE.compareAndSet(this, oldValue, newValue);
        }
    }

    public static class OfIntArray extends StableValueImpl<Integer> {

        @Stable
        final int[] x;

        static final VarHandle RAW_HANDLE;
        static final VarHandle HANDLE;

        static {
            try {
                RAW_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);
                HANDLE = MethodHandles.filterCoordinates(RAW_HANDLE, 0,
                        MethodHandles.lookup().findGetter(OfIntArray.class, "x", int[].class));
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        @Override
        public Integer defaultValue() {
            return 0;
        }

        @Override
        public VarHandle varHandle() {
            return HANDLE;
        }

        public OfIntArray(int dim0) {
            this.x = new int[dim0];
        }

        @Override
        public Integer get(int... indices) {
            return (Integer)RAW_HANDLE.get(x, indices[0]);
        }

        @Override
        public boolean cas(Integer oldValue, Integer newValue, int... indices) {
            return RAW_HANDLE.compareAndSet(x, oldValue, newValue, indices[0]);
        }
    }

    public static class OfIntArrayArray extends StableValueImpl<Integer> {

        @Stable
        int[][] x;

        static final VarHandle RAW_HANDLE;
        static final VarHandle HANDLE;

        static {
            try {
                VarHandle ELEM_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);
                RAW_HANDLE = MethodHandles.collectCoordinates(ELEM_HANDLE, 0,
                        MethodHandles.arrayElementVarHandle(int[][].class).toMethodHandle(AccessMode.GET));
                HANDLE = MethodHandles.filterCoordinates(RAW_HANDLE, 0,
                        MethodHandles.lookup().findGetter(OfIntArrayArray.class, "x", int[][].class));
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        @Override
        public Integer defaultValue() {
            return 0;
        }

        @Override
        public VarHandle varHandle() {
            return HANDLE;
        }

        public OfIntArrayArray(int dim0, int dim1) {
            this.x = new int[dim0][dim1];
        }

        @Override
        public Integer get(int... indices) {
            return (Integer)RAW_HANDLE.get(x, indices[0], indices[1]);
        }

        @Override
        public boolean cas(Integer oldValue, Integer newValue, int... indices) {
            return RAW_HANDLE.compareAndSet(x, oldValue, newValue, indices[0], indices[1]);
        }
    }

    public static class OfObject extends StableValueImpl<Object> {

        @Stable
        Object x;

        static final VarHandle RAW_HANDLE;

        static {
            try {
                RAW_HANDLE = MethodHandles.lookup()
                        .findVarHandle(OfObject.class, "x", Object.class);
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        @Override
        public Object defaultValue() {
            return null;
        }

        @Override
        public VarHandle varHandle() {
            return RAW_HANDLE;
        }

        @Override
        public Object get(int... indices) {
            return RAW_HANDLE.get(this);
        }

        @Override
        public boolean cas(Object oldValue, Object newValue, int... indices) {
            return RAW_HANDLE.compareAndSet(this, oldValue, newValue);
        }
    }

    public static class OfObjectArray extends StableValueImpl<Object> {

        @Stable
        final Object[] x;

        static final VarHandle RAW_HANDLE;
        static final VarHandle HANDLE;

        static {
            try {
                RAW_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
                HANDLE = MethodHandles.filterCoordinates(RAW_HANDLE, 0,
                        MethodHandles.lookup().findGetter(OfObjectArray.class, "x", Object[].class));
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        @Override
        public Object defaultValue() {
            return null;
        }

        @Override
        public VarHandle varHandle() {
            return HANDLE;
        }

        public OfObjectArray(int dim0) {
            this.x = new Object[dim0];
        }

        @Override
        public Object get(int... indices) {
            return RAW_HANDLE.get(x, indices[0]);
        }

        @Override
        public boolean cas(Object oldValue, Object newValue, int... indices) {
            return RAW_HANDLE.compareAndSet(x, oldValue, newValue, indices[0]);
        }
    }

    public static class OfObjectArrayArray extends StableValueImpl<Object> {

        @Stable
        Object[][] x;

        static final VarHandle RAW_HANDLE;
        static final VarHandle HANDLE;

        static {
            try {
                VarHandle ELEM_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
                RAW_HANDLE = MethodHandles.collectCoordinates(ELEM_HANDLE, 0,
                        MethodHandles.arrayElementVarHandle(Object[][].class).toMethodHandle(AccessMode.GET));
                HANDLE = MethodHandles.filterCoordinates(RAW_HANDLE, 0,
                        MethodHandles.lookup().findGetter(OfObjectArrayArray.class, "x", Object[][].class));
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        @Override
        public Object defaultValue() {
            return null;
        }

        @Override
        public VarHandle varHandle() {
            return HANDLE;
        }

        public OfObjectArrayArray(int dim0, int dim1) {
            this.x = new Object[dim0][dim1];
        }

        @Override
        public Object get(int... indices) {
            return RAW_HANDLE.get(x, indices[0], indices[1]);
        }

        @Override
        public boolean cas(Object oldValue, Object newValue, int... indices) {
            return RAW_HANDLE.compareAndSet(x, oldValue, newValue, indices[0], indices[1]);
        }
    }
}
