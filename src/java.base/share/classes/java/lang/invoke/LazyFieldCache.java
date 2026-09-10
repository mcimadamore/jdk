/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.util.Objects;
import java.util.function.Function;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * A non-owning cache backed by an instance field, with a computing function
 * supplied at the declaration site.
 *
 * @param <R> the receiver type
 * @param <T> the field value type
 */
public interface LazyFieldCache<R, T> {
    /**
     * Lazy-initialization mode.
     */
    enum Mode {
        /** Computations may race; one successful result is published with CAS. */
        RETRY,
        /** Computation is serialized on the receiver; one successful result is published. */
        ONCE
    }

    /**
     * Returns the cached value with stable semantics, computing and publishing it
     * atomically if unset.
     *
     * @param receiver the field receiver
     * @return the cached value
     */
    T get(R receiver);

    /**
     * Creates a cache backed by an instance field.
     *
     * @param mode the lazy-initialization mode
     * @param owner the field declaring class
     * @param name the field name
     * @param computer the computing function
     * @param <R> the receiver type
     * @param <T> the field value type
     * @return the cache
     */
    @CallerSensitive
    static <R, T> LazyFieldCache<R, T> ofField(Mode mode,
                                               Class<? super R> owner,
                                               String name,
                                               Function<? super R, ? extends T> computer) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        Objects.requireNonNull(computer);
        return LazyFieldCacheImpl.ofField(mode, owner, name, computer, Reflection.getCallerClass());
    }
}
