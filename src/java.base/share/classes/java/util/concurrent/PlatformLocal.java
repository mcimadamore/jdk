package java.util.concurrent;

import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A cache of a memory segments to allow reuse. In many cases, only one segment will
 * ever be allocated per platform thread. However, if a virtual thread becomes
 * unmounted from its platform thread and another virtual thread acquires a new
 * segment, there will be no reuse. Having a secondary cache of these is slower than
 * using malloc/free directly.
 * <p>
 * The cache is about three times faster than a more general `deque`.
 * <p>
 * The class is using j.i.m.Unsafe rather than other supported APIs to allow early
 * use in the boostrap sequence.
 * @param <T> the type of the platform local
 */
public class PlatformLocal<T> {
    private final TerminatingThreadLocal<Cache> TL_CACHE = new TerminatingThreadLocal<>() {
        @Override
        protected void threadTerminated(Cache cache) {
            cache.close();
        }

        @Override
        protected Cache initialValue() {
            return new Cache();
        }
    };

    private final Supplier<T> valueSupplier;
    private final Consumer<T> valueDisposer;

    /**
     * Constructs a new platform local
     * @param valueSupplier supplier of platform local value
     * @param valueDisposer disposer of platform local value
     */
    public PlatformLocal(Supplier<T> valueSupplier, Consumer<T> valueDisposer) {
        this.valueSupplier = valueSupplier;
        this.valueDisposer = valueDisposer;
    }

    private class Cache {

        static final Unsafe UNSAFE = Unsafe.getUnsafe();

        private static final long ALLOCATED_OFFSET =
                UNSAFE.objectFieldOffset(PlatformLocal.Cache.class, "lock");

        // Using an int lock is faster than CASing a reference field
        private int lock;
        @Stable
        private final T cachedValue = valueSupplier.get();

        @ForceInline
        private T acquire() {
            return lock() ? cachedValue : valueSupplier.get();
        }

        // Used reflectively
        @ForceInline
        private void release(T value) {
            if (value == cachedValue) {
                unlock();
            } else {
                valueDisposer.accept(value);
            }
        }

        @ForceInline
        private boolean lock() {
            return UNSAFE.getAndSetInt(this, ALLOCATED_OFFSET, 1) == 0;
        }

        @ForceInline
        private void unlock() {
            UNSAFE.putIntVolatile(this, ALLOCATED_OFFSET, 0);
        }

        // This method is called by a separate cleanup thread when the associated
        // platform thread is dead.
        private void close() {
            valueDisposer.accept(cachedValue);
        }
    }

    /** Blah
     *
     * @param <T> blah
     */
    public sealed interface Value<T> extends AutoCloseable {
        /**
         * Blah
         * @return blah
         */
        T get();

        @Override
        void close();
    }

    private final class OfCache implements Value<T> {
        final T t;
        final Cache cache;

        OfCache(Cache cache) {
            this.t = cache.acquire();
            this.cache = cache;
        }

        @Override
        public void close() {
            cache.release(t);
        }

        @Override
        public T get() {
            return t;
        }
    }

    private record OfSupplied<T>(T t) implements Value<T> {
        @Override
        public void close() {
            // do nothing
        }

        @Override
        public T get() {
            return t;
        }
    }

    /**
     * Blah
     * @return blah
     */
    public Value<T> get() {
        Cache cache = TL_CACHE.get();
        boolean needsAcquire = Thread.currentThread().isVirtual();
        return needsAcquire ?
                new OfCache(cache) :
                new OfSupplied<>(cache.cachedValue);
    }

    /**
     * Obtains the platform local value associated with the current thread and perform
     * an action on it.
     * @param valueConsumer the action to perform on the platform local value.
     */
    @ForceInline
    public void runWith(Consumer<T> valueConsumer) {
        Cache cache = TL_CACHE.get();
        boolean needsAcquire = Thread.currentThread().isVirtual();
        T t = needsAcquire ? cache.acquire() : cache.cachedValue;
        valueConsumer.accept(t);
        if (needsAcquire) {
            cache.release(t);
        }
    }

    /**
     * Obtains the platform local value associated with the current thread and perform
     * an action on it.
     * @param valueFunction the action to perform on the platform local value.
     * @param <R> return value
     * @return blah
     */
    @ForceInline
    @SuppressWarnings("overloads")
    public <R> R runWith(Function<T, R> valueFunction) {
        Cache cache = TL_CACHE.get();
        boolean needsAcquire = Thread.currentThread().isVirtual();
        T t = needsAcquire ? cache.acquire() : cache.cachedValue;
        try {
            return valueFunction.apply(t);
        } finally {
            if (needsAcquire) {
                cache.release(t);
            }
        }
    }
}
