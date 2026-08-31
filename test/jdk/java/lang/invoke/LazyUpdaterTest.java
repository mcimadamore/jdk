/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Tests lazy updater method handle combinators and lazy values
 * @run main LazyUpdaterTest
 */

import java.lang.invoke.LazyArray;
import java.lang.invoke.LazyValue;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class LazyUpdaterTest {
    static final class Box {
        private Object plain;
        private volatile Object atomic;
        private volatile Object locked;
        private int number;

    }

    private static Object plainValue(Box box) {
        return "plain";
    }

    private static Object atomicValue(Box box) {
        return "atomic";
    }

    private static Object lockedValue(Box box) {
        return "locked";
    }

    private static int numberValue(Box box) {
        return 42;
    }

    private static int arrayValue(int[] array, int index) {
        return index + 1;
    }

    private static int zeroValue(Box box) {
        return 0;
    }

    public static void main(String[] args) throws Throwable {
        testMethodHandleCombinators();
        testLazyValues();
        testVolatileRace();
        testSynchronizedComputation();
    }

    private static void testMethodHandleCombinators() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Box box = new Box();

        VarHandle plainTarget = lookup.findVarHandle(Box.class, "plain", Object.class);
        MethodHandle plainInitializer = lookup.findStatic(LazyUpdaterTest.class,
                "plainValue", MethodType.methodType(Object.class, Box.class));
        MethodHandle plain = MethodHandles.lazyUpdater(plainTarget, plainInitializer, true);
        assertEquals("plain", (Object) plain.invokeExact(box));
        assertEquals("plain", box.plain);

        VarHandle atomicTarget = lookup.findVarHandle(Box.class, "atomic", Object.class);
        MethodHandle atomicInitializer = lookup.findStatic(LazyUpdaterTest.class,
                "atomicValue", MethodType.methodType(Object.class, Box.class));
        MethodHandle atomic = MethodHandles.lazyUpdaterVolatile(
                atomicTarget, atomicInitializer, true);
        assertEquals("atomic", (Object) atomic.invokeExact(box));
        assertEquals("atomic", box.atomic);

        VarHandle lockedTarget = lookup.findVarHandle(Box.class, "locked", Object.class);
        MethodHandle lockedInitializer = lookup.findStatic(LazyUpdaterTest.class,
                "lockedValue", MethodType.methodType(Object.class, Box.class));
        MethodHandle locked = MethodHandles.lazyUpdaterSynchronized(
                lockedTarget, lockedInitializer, true);
        assertEquals(MethodType.methodType(Object.class, Object.class, Box.class),
                locked.type());
        assertEquals("locked", (Object) locked.invokeExact((Object) box, box));
        assertEquals("locked", box.locked);

        VarHandle numberTarget = lookup.findVarHandle(Box.class, "number", int.class);
        MethodHandle numberInitializer = lookup.findStatic(LazyUpdaterTest.class,
                "numberValue", MethodType.methodType(int.class, Box.class));
        MethodHandle number = MethodHandles.lazyUpdaterVolatile(
                numberTarget, numberInitializer, false);
        assertEquals(42, (int) number.invokeExact(box));

        VarHandle arrayTarget = MethodHandles.arrayElementVarHandle(int[].class);
        MethodHandle arrayInitializer = lookup.findStatic(LazyUpdaterTest.class,
                "arrayValue", MethodType.methodType(int.class, int[].class, int.class));
        MethodHandle array = MethodHandles.lazyUpdaterVolatile(
                arrayTarget, arrayInitializer, false);
        int[] values = new int[2];
        assertEquals(2, (int) array.invokeExact(values, 1));
        assertEquals(2, values[1]);

        MethodHandle zeroInitializer = lookup.findStatic(LazyUpdaterTest.class,
                "zeroValue", MethodType.methodType(int.class, Box.class));
        MethodHandle rejecting = MethodHandles.lazyUpdater(numberTarget, zeroInitializer, false);
        expectThrows(IllegalStateException.class, () -> {
            int ignored = (int) rejecting.invokeExact(new Box());
        });
    }

    private static void testLazyValues() {
        LazyValue<Box, String> plain = LazyValue.of(LazyValue.Policy.PLAIN, box -> "plain");
        LazyValue<Box, String> atomic = LazyValue.of(LazyValue.Policy.CAS, box -> "atomic");
        LazyValue<Box, String> once = LazyValue.of(LazyValue.Policy.ONCE, box -> "once");
        Box box = new Box();
        assertEquals("plain", plain.get(box));
        assertEquals("atomic", atomic.get(box));
        assertEquals("once", once.get(box));

        AtomicInteger attempts = new AtomicInteger();
        LazyValue<Box, String> retry = LazyValue.of(LazyValue.Policy.CAS, receiver -> {
            if (attempts.getAndIncrement() == 0) {
                throw new TestException();
            }
            return "retried";
        });
        expectThrows(TestException.class, () -> retry.get(box));
        assertEquals("retried", retry.get(box));
        assertEquals(2, attempts.get());

        AtomicInteger nullAttempts = new AtomicInteger();
        LazyValue<Box, String> nullResult = LazyValue.of(LazyValue.Policy.CAS, receiver -> {
            nullAttempts.incrementAndGet();
            return null;
        });
        expectThrows(NullPointerException.class, () -> nullResult.get(box));
        expectThrows(NullPointerException.class, () -> nullResult.get(box));
        assertEquals(2, nullAttempts.get());

        LazyArray<Void, Integer> array = LazyArray.of(LazyValue.Policy.CAS, 3, index -> index + 1);
        assertEquals(3, array.get(null, 2));

        Supplier<String> supplier = Supplier.ofLazy(() -> "supplier");
        assertEquals("supplier", supplier.get());
    }

    private static void testVolatileRace() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger computations = new AtomicInteger();
        LazyValue<Box, String> cache = LazyValue.of(LazyValue.Policy.CAS, box -> {
                    int id = computations.incrementAndGet();
                    await(barrier);
                    return "candidate-" + id;
                });
        Box box = new Box();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> cache.get(box));
            Future<String> second = executor.submit(() -> cache.get(box));
            String firstValue = first.get();
            String secondValue = second.get();
            assertEquals(firstValue, secondValue);
            assertEquals(2, computations.get());
        }
    }

    private static void testSynchronizedComputation() throws Exception {
        AtomicInteger computations = new AtomicInteger();
        LazyValue<Box, String> cache = LazyValue.of(LazyValue.Policy.ONCE, box -> {
                    computations.incrementAndGet();
                    return "once";
                });
        Box box = new Box();

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            @SuppressWarnings("unchecked")
            Future<String>[] futures = new Future[8];
            for (int i = 0; i < futures.length; i++) {
                futures[i] = executor.submit(() -> cache.get(box));
            }
            for (Future<String> future : futures) {
                assertEquals("once", future.get());
            }
        }
        assertEquals(1, computations.get());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expectThrows(Class<? extends Throwable> expected,
                                     ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable ex) {
            if (expected.isInstance(ex)) {
                return;
            }
            throw new AssertionError("Unexpected exception", ex);
        }
        throw new AssertionError("Expected " + expected.getName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static final class TestException extends RuntimeException {
    }
}
