/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 */

package java.lang.invoke;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A value computed lazily from an argument supplied at access time.
 *
 * @param <T> the value type
 */
public abstract sealed class AbstractLazyValueUseSite<T>
        permits AbstractLazyValueUseSiteImpl.OfPlain,
                AbstractLazyValueUseSiteImpl.OfCas,
                AbstractLazyValueUseSiteImpl.OfOnce {
    /**
     * Lazy-update policy.
     */
    public enum Policy {
        /** Computations and publications use plain accesses and may race. */
        PLAIN() {
            @Override
            <T> AbstractLazyValueUseSite<T> make() {
                return AbstractLazyValueUseSiteImpl.ofPlain();
            }
        },
        /** Computations may race, but only one successful outcome is published. */
        CAS() {
            @Override
            <T> AbstractLazyValueUseSite<T> make() {
                return AbstractLazyValueUseSiteImpl.ofCas();
            }
        },
        /** One outcome is computed under synchronization and is then remembered. */
        ONCE() {
            @Override
            <T> AbstractLazyValueUseSite<T> make() {
                return AbstractLazyValueUseSiteImpl.ofOnce();
            }
        };

        abstract <T> AbstractLazyValueUseSite<T> make();
    }

    AbstractLazyValueUseSite() { }

    /**
     * Returns the value, computing it with {@code computer} from {@code argument} when needed.
     * Subsequent arguments are ignored after an outcome is published.
     *
     * @param computer the computing function
     * @param argument the computing-function argument
     * @param <A> the computing-function argument type
     * @return the lazy value
     */
    public abstract <A> T get(A argument, Function<? super A, ? extends T> computer);

    /**
     * Returns the value, computing it with {@code supplier} when needed.
     *
     * @param supplier the computing supplier
     * @return the lazy value
     */
    public T get(Supplier<? extends T> supplier) {
        return get(null, ignored -> supplier.get());
    }

    /**
     * Creates a lazy value with the {@link Policy#ONCE} policy.
     *
     * @param <T> the value type
     * @return the lazy value
     */
    public static <T> AbstractLazyValueUseSite<T> of() {
        return Policy.ONCE.make();
    }

    /**
     * Creates a lazy value with the supplied policy.
     *
     * @param policy the lazy-update policy
     * @param <T> the value type
     * @return the lazy value
     */
    public static <T> AbstractLazyValueUseSite<T> of(Policy policy) {
        Objects.requireNonNull(policy);
        return policy.make();
    }
}
