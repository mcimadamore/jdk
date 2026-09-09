/*
 * @test /nodynamiccopyright/
 * @enablePreview
 * @summary Test cached method initialization modes under contention
 * @run main/othervm ConcurrentInitTest cas
 * @run main/othervm -Djdk.cachedMethods.initMode=cas ConcurrentInitTest cas
 * @run main/othervm -Djdk.cachedMethods.initMode=plain ConcurrentInitTest plain
 * @run main/othervm -Djdk.cachedMethods.initMode=synchronized ConcurrentInitTest synchronized
 */

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentInitTest {
    static final int THREADS = 8;

    static volatile Mode mode;
    static CyclicBarrier staticBarrier;
    static CyclicBarrier instanceBarrier;
    static AtomicInteger staticInitCount = new AtomicInteger();
    static AtomicInteger instanceInitCount = new AtomicInteger();

    record Test(int x) {
        cached static int m_s() {
            return initStatic();
        }

        cached int m_i() {
            return initInstance(x);
        }
    }

    public static void main(String[] args) throws Exception {
        mode = args.length == 0 ? Mode.CAS : Mode.valueOf(args[0].toUpperCase());
        int expectedInitCount = mode == Mode.SYNCHRONIZED ? 1 : THREADS;

        staticBarrier = new CyclicBarrier(THREADS);
        assertResults(runConcurrently(() -> Test.m_s()), 101);
        assertEquals(expectedInitCount, staticInitCount.get());

        Test test = new Test(42);
        instanceBarrier = new CyclicBarrier(THREADS);
        assertResults(runConcurrently(() -> test.m_i()), 43);
        assertEquals(expectedInitCount, instanceInitCount.get());
    }

    static int initStatic() {
        int count = staticInitCount.incrementAndGet();
        waitForContenders(staticBarrier);
        return 100 + count;
    }

    static int initInstance(int value) {
        int count = instanceInitCount.incrementAndGet();
        waitForContenders(instanceBarrier);
        return value + count;
    }

    static void waitForContenders(CyclicBarrier barrier) {
        try {
            if (mode == Mode.SYNCHRONIZED) {
                Thread.sleep(100);
            } else {
                barrier.await(30, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    static int[] runConcurrently(ThrowingIntSupplier action) throws Exception {
        int[] results = new int[THREADS];
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[THREADS];

        CyclicBarrier start = new CyclicBarrier(THREADS);
        for (int i = 0; i < THREADS; i++) {
            int index = i;
            threads[i] = new Thread(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    results[index] = action.getAsInt();
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(60));
            if (thread.isAlive()) {
                thread.interrupt();
                throw new AssertionError("worker did not complete");
            }
        }

        Throwable firstFailure = failure.get();
        if (firstFailure != null) {
            throw new AssertionError(firstFailure);
        }

        return results;
    }

    static void assertResults(int[] values, int minValue) {
        for (int value : values) {
            if (mode == Mode.PLAIN) {
                if (value < minValue || value >= minValue + THREADS) {
                    throw new AssertionError(value);
                }
            } else {
                assertEquals(values[0], value);
            }
        }
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    interface ThrowingIntSupplier {
        int getAsInt() throws Throwable;
    }

    enum Mode {
        CAS,
        PLAIN,
        SYNCHRONIZED
    }
}
