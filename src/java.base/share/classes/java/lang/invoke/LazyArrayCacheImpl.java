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

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;
import static java.lang.invoke.MethodType.methodType;

final class LazyArrayCacheImpl<A, T> implements LazyArrayCache<A, T> {
    private static final MethodHandle COMPUTER_APPLY;

    static {
        try {
            COMPUTER_APPLY = MethodHandles.Lookup.IMPL_LOOKUP.findVirtual(
                    LazyArrayCache.Computer.class,
                    "compute",
                    methodType(Object.class, Object.class, int.class));
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private final VarHandle target;
    private final MethodHandle initializer;

    @Stable
    private volatile MethodHandle plain;
    @Stable
    private volatile MethodHandle volatileUpdater;
    @Stable
    private volatile MethodHandle synchronizedUpdater;

    static <A, T> LazyArrayCache<A, T> of(
            Class<A> arrayClass,
            LazyArrayCache.Computer<? super A, ? extends T> computer) {
        if (!arrayClass.isArray()) {
            throw new IllegalArgumentException(arrayClass.getName() + " is not an array class");
        }
        VarHandle target = MethodHandles.arrayElementVarHandle(arrayClass);
        MethodHandle initializer = COMPUTER_APPLY.bindTo(computer)
                .asType(target.accessModeType(VarHandle.AccessMode.GET));
        return new LazyArrayCacheImpl<>(target, initializer);
    }

    private LazyArrayCacheImpl(VarHandle target, MethodHandle initializer) {
        this.target = target;
        this.initializer = initializer;
    }

    @Override
    @SuppressWarnings("unchecked")
    @ForceInline
    public T get(A array, int index) {
        MethodHandle updater = plain;
        if (updater == null) {
            updater = initializePlain();
        }
        try {
            return (T) (Object) updater.invokeExact((Object) array, index);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    @ForceInline
    public T getVolatile(A array, int index) {
        MethodHandle updater = volatileUpdater;
        if (updater == null) {
            updater = initializeVolatile();
        }
        try {
            return (T) (Object) updater.invokeExact((Object) array, index);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    @ForceInline
    public T getSynchronized(Object lock, A array, int index) {
        MethodHandle updater = synchronizedUpdater;
        if (updater == null) {
            updater = initializeSynchronized();
        }
        try {
            return (T) (Object) updater.invokeExact(lock, (Object) array, index);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
    }

    @DontInline
    private synchronized MethodHandle initializePlain() {
        if (plain == null) {
            plain = MethodHandles.lazyUpdater(target, initializer, false)
                    .asType(methodType(Object.class, Object.class, int.class));
        }
        return plain;
    }

    @DontInline
    private synchronized MethodHandle initializeVolatile() {
        if (volatileUpdater == null) {
            volatileUpdater = MethodHandles.lazyUpdaterVolatile(target, initializer, false)
                    .asType(methodType(Object.class, Object.class, int.class));
        }
        return volatileUpdater;
    }

    @DontInline
    private synchronized MethodHandle initializeSynchronized() {
        if (synchronizedUpdater == null) {
            synchronizedUpdater = MethodHandles.lazyUpdaterSynchronized(target, initializer, false)
                    .asType(methodType(Object.class, Object.class, Object.class, int.class));
        }
        return synchronizedUpdater;
    }
}
