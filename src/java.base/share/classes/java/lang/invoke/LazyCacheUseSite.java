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

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * A non-owning cache backed by an instance field, with a computing function
 * supplied at the use site.
 *
 * @param <R> the receiver type
 * @param <T> the field value type
 */
public interface LazyCacheUseSite<R, T> {
    /**
     * Lazy-update policy.
     */
    enum Policy {
        /** Computations and publications use plain accesses and may race. */
        PLAIN() {
            @Override
            <R, T> LazyCacheUseSite<R, T> make(Class<R> owner, String name, Class<?> caller) {
                return LazyCacheUseSiteImpl.ofPlain(owner, name, caller);
            }
        },
        /** Computations may race, but only one successful outcome is published. */
        CAS() {
            @Override
            <R, T> LazyCacheUseSite<R, T> make(Class<R> owner, String name, Class<?> caller) {
                return LazyCacheUseSiteImpl.ofCas(owner, name, caller);
            }
        },
        /** A value is computed under synchronization and is then remembered. */
        ONCE() {
            @Override
            <R, T> LazyCacheUseSite<R, T> make(Class<R> owner, String name, Class<?> caller) {
                return LazyCacheUseSiteImpl.ofOnce(owner, name, caller);
            }
        };

        abstract <R, T> LazyCacheUseSite<R, T> make(Class<R> owner, String name,
                                                    Class<?> caller);
    }

    /**
     * Returns the cached value, computing it if unset according to this cache's policy.
     *
     * @param receiver the field receiver and computing-function argument
     * @param computer the computing function
     * @return the cached value
     */
    T get(R receiver, Function<? super R, ? extends T> computer);

    /**
     * Creates a cache backed by an instance field.
     *
     * @param owner the field declaring class
     * @param name the field name
     * @param policy the lazy-update policy
     * @param <R> the receiver type
     * @param <T> the field value type
     * @return the cache
     */
    @CallerSensitive
    static <R, T> LazyCacheUseSite<R, T> ofField(Class<R> owner, String name,
                                                 Policy policy) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        Objects.requireNonNull(policy);
        return policy.make(owner, name, Reflection.getCallerClass());
    }
}
