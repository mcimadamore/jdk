/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.lang;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The sole implementation of the LazyConstant interface.
 *
 * @param <T> type of the constant
 * @implNote This implementation can be used early in the boot sequence as it does not
 * rely on reflection, MethodHandles, Streams etc.
 */
@AOTSafeClassInitializer
public final class LazyConstantImpl<T> implements LazyConstant<T> {

    private static final String RECURSIVE_INVOCATION_MESSAGE =
            "Recursive invocation of a LazyConstant's computing function: ";

    // Unsafe allows `LazyConstant` instances to be used early in the boot sequence
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Unsafe offset for access of the `constant` field
    private static final long CONSTANT_OFFSET =
            UNSAFE.objectFieldOffset(LazyConstantImpl.class, "constant");

    // Unsafe offset for access of the `computingFunctionOrExceptionType` field
    private static final long STATE_OFFSET =
            UNSAFE.objectFieldOffset(LazyConstantImpl.class,
                    "computingFunctionOrExceptionType");

    // Generally, fields annotated with `@Stable` are accessed by the JVM using special
    // memory semantics rules (see `parse.hpp` and `parse(1|2|3).cpp`).
    //
    // This field is used reflectively via Unsafe using explicit memory semantics.
    //
    // | Value           | Meaning        |
    // | --------------- | -------------- |
    // | `null`          | Unset          |
    // | `other`         | Set to `other` |
    //
    @Stable
    private T constant;

    // This field tracks the initialization state:
    //
    // | Value      | Meaning                                      |
    // | ---------- | -------------------------------------------- |
    // | `Supplier` | Unset                                        |
    // | `Thread`   | Being initialized by that thread             |
    // | `null`     | Set                                          |
    // | `String`   | Failed with the named exception type         |
    //
    // The field needs to be `volatile` as a lazy constant can be created by one
    // thread and computed by another thread. Explicit memory semantics are used
    // for all accesses after construction.
    private volatile Object computingFunctionOrExceptionType;

    private LazyConstantImpl(Supplier<? extends T> computingFunction) {
        this.computingFunctionOrExceptionType = computingFunction;
    }

    @ForceInline
    @Override
    public T get() {
        final T t = getAcquire();
        return (t != null) ? t : getSlowPath();
    }

    @DontInline
    private T getSlowPath() {
        final Thread current = Thread.currentThread();
        Object state = getStateAcquire();
        while (true) {
            // Don't use switch pattern matching here in order to improve startup time.
            if (state instanceof Supplier<?> computingFunction) {
                final Object witness = UNSAFE.compareAndExchangeReference(
                        this, STATE_OFFSET, state, current);
                if (witness == state) {
                    return initialize(computingFunction);
                }
                state = witness;
            } else if (state instanceof Thread) {
                if (state == current) {
                    throw new RecursiveInitializationException();
                }
                Thread.onSpinWait();
                state = getStateAcquire();
            } else if (state instanceof String exceptionType) {
                throw unableToAccessConstant(exceptionType, null);
            } else {
                assert state == null;
                final T t = getAcquire();
                assert t != null;
                return t;
            }
        }
    }

    private T initialize(Supplier<?> computingFunction) {
        try {
            @SuppressWarnings("unchecked")
            final T t = (T) computingFunction.get();
            Objects.requireNonNull(t);
            setRelease(t);
            // Release the underlying supplier after successful initialization.
            setStateRelease(null);
            return t;
        } catch (Throwable ex) {
            if (ex instanceof RecursiveInitializationException) {
                ex = new IllegalStateException(RECURSIVE_INVOCATION_MESSAGE +
                        isolateToString(computingFunction));
            }
            // Release the original computing function and replace it with an
            // exception marker.
            final String exceptionType = ex.getClass().getName().intern();
            setStateRelease(exceptionType);
            throw unableToAccessConstant(exceptionType, ex);
        }
    }

    static NoSuchElementException unableToAccessConstant(String exceptionType, Throwable cause) {
        return new NoSuchElementException("Unable to access the constant because " +
                exceptionType + " was thrown at initial computation", cause);
    }

    // For testing only
    @ForceInline
    public T orElse(T other) {
        final T t = getAcquire();
        return (t == null) ? other : t;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + toStringSuffix() + "]";
    }

    private String toStringSuffix() {
        final T t = getAcquire();
        if (t == this) {
            return "(this LazyConstant)";
        } else if (t != null) {
            return t.toString();
        } else {
            final Object cf = getStateAcquire();
            // There could be a race here
            if (cf != null) {
                if (cf instanceof Supplier<?> supplier) {
                    return "computing function=" + isolateToString(supplier);
                } else if (cf instanceof Thread thread) {
                    return "computing thread=" + isolateToString(thread);
                } else {
                    return "failed with=" + cf;
                }
            }
            // As we know `computingFunction` is `null` or via a volatile read, we
            // can now be sure that this lazy constant is initialized
            return getAcquire().toString();
        }
    }


    // Discussion on the memory semantics used.
    // ----------------------------------------
    // Using acquire/release semantics on the `constant` field is the cheapest way to
    // establish a happens-before (HB) relation between load and store operations. Every
    // implementation of a method defined in the interface `LazyConstant` except
    // `equals()` starts with a load of the `constant` field using acquire semantics.
    //
    // If the underlying supplier was guaranteed to always create a new object,
    // a fence after creation and subsequent plain loads would suffice to ensure
    // new objects' state are always correctly observed. However, no such restriction is
    // imposed on the underlying supplier. Hence, the docs state there should be an
    // HB relation meaning we will have to pay a price (on certain platforms) on every
    // `get()` operation that is not constant-folded.

    @SuppressWarnings("unchecked")
    @ForceInline
    private T getAcquire() {
        return (T) UNSAFE.getReferenceAcquire(this, CONSTANT_OFFSET);
    }

    private void setRelease(T newValue) {
        UNSAFE.putReferenceRelease(this, CONSTANT_OFFSET, newValue);
    }

    private Object getStateAcquire() {
        return UNSAFE.getReferenceAcquire(this, STATE_OFFSET);
    }

    private void setStateRelease(Object state) {
        UNSAFE.putReferenceRelease(this, STATE_OFFSET, state);
    }

    private static final class RecursiveInitializationException
            extends IllegalStateException {
        private static final long serialVersionUID = 1L;
    }

    public static String isolateToString(Object input) {
        // Protect against user-controlled `input.toString` methods that might throw or recurse.
        try {
            return input.toString();
        } catch (Throwable t) {
            return Objects.toIdentityString(input);
        }
    }

    // Factory

    public static <T> LazyConstantImpl<T> ofLazy(Supplier<? extends T> computingFunction) {
        return new LazyConstantImpl<>(computingFunction);
    }

}
