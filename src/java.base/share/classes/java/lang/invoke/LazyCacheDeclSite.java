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
public interface LazyCacheDeclSite<R, T> {
    /**
     * Returns the cached value, computing and storing it with plain semantics if unset.
     *
     * @param receiver the field receiver
     * @return the cached value
     */
    T get(R receiver);

    /**
     * Returns the cached value, computing it if unset and publishing it atomically.
     *
     * @param receiver the field receiver
     * @return the cached value
     */
    T getVolatile(R receiver);

    /**
     * Returns the cached value, computing it while holding {@code lock} if unset.
     *
     * @param lock the computation lock
     * @param receiver the field receiver
     * @return the cached value
     */
    T getSynchronized(Object lock, R receiver);

    /**
     * Creates a cache backed by an instance field.
     *
     * @param owner the field declaring class
     * @param name the field name
     * @param computer the computing function
     * @param <R> the receiver type
     * @param <T> the field value type
     * @return the cache
     */
    @CallerSensitive
    static <R, T> LazyCacheDeclSite<R, T> ofField(Class<R> owner,
                                                  String name,
                                                  Function<? super R, ? extends T> computer) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        Objects.requireNonNull(computer);
        return LazyCacheDeclSiteImpl.ofField(owner, name, computer, Reflection.getCallerClass());
    }
}
