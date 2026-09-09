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

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A value computed lazily from an argument supplied at access time.
 *
 * @param <T> the value type
 */
public interface LazyCache<T> {
    /**
     * Returns the value, computing it with {@code computer} from {@code argument} when needed.
     * Subsequent arguments are ignored after an outcome is published.
     *
     * @param computer the computing function
     * @param argument the computing-function argument
     * @param <A> the computing-function argument type
     * @return the lazy value
     */
    <A> T getOrCompute(A argument, Function<? super A, ? extends T> computer);

    /**
     * Returns the value, computing it with {@code supplier} when needed.
     *
     * @param supplier the computing supplier
     * @return the lazy value
     */
    default T getOrCompute(Supplier<? extends T> supplier) {
        return getOrCompute(null, ignored -> supplier.get());
    }

    /**
     * Creates a cache which retries failed computations and permits racing computations.
     *
     * @param <T> the value type
     * @return the lazy value
     */
    static <T> LazyCache<T> ofRetry() {
        return LazyCacheImpl.ofRetry();
    }

    /**
     * Creates a cache which computes and remembers one successful or failed outcome.
     * @param <T> the value type
     * @return the lazy value
     */
    static <T> LazyCache<T> ofOnce() {
        return LazyCacheImpl.ofOnce();
    }
}
