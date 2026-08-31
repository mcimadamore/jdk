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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import static java.lang.invoke.MethodHandleStatics.uncaughtException;
import static java.lang.invoke.MethodType.methodType;

final class LazyCacheImpl<R, T> implements LazyCache<R, T> {
    private static final MethodHandle FUNCTION_APPLY;

    static {
        try {
            FUNCTION_APPLY = MethodHandles.Lookup.IMPL_LOOKUP.findVirtual(
                    Function.class, "apply", methodType(Object.class, Object.class));
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

    static <R, T> LazyCache<R, T> ofField(Class<R> owner,
                                          String name,
                                          Function<? super R, ? extends T> computer,
                                          Class<?> caller) {
        try {
            Field field = owner.getDeclaredField(name);
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                throw new IllegalArgumentException(name + " is not an instance field");
            }
            if (Modifier.isFinal(modifiers)) {
                throw new IllegalArgumentException(name + " is a final field");
            }
            MethodHandles.Lookup lookup = new MethodHandles.Lookup(caller);
            VarHandle target = lookup.findVarHandle(
                    owner, name, field.getType());
            MethodHandle initializer = FUNCTION_APPLY.bindTo(computer)
                    .asType(target.accessModeType(VarHandle.AccessMode.GET));
            return new LazyCacheImpl<>(target, initializer);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalArgumentException("Cannot access " + owner.getName() + "." + name, ex);
        }
    }

    private LazyCacheImpl(VarHandle target, MethodHandle initializer) {
        this.target = target;
        this.initializer = initializer;
    }

    @Override
    @SuppressWarnings("unchecked")
    @ForceInline
    public T get(R receiver) {
        MethodHandle updater = plain;
        if (updater == null) {
            updater = initializePlain();
        }
        try {
            return (T) (Object) updater.invokeExact((Object) receiver);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    @ForceInline
    public T getVolatile(R receiver) {
        MethodHandle updater = volatileUpdater;
        if (updater == null) {
            updater = initializeVolatile();
        }
        try {
            return (T) (Object) updater.invokeExact((Object) receiver);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    @ForceInline
    public T getSynchronized(Object lock, R receiver) {
        MethodHandle updater = synchronizedUpdater;
        if (updater == null) {
            updater = initializeSynchronized();
        }
        try {
            return (T) (Object) updater.invokeExact(lock, (Object) receiver);
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
    }

    @DontInline
    private synchronized MethodHandle initializePlain() {
        if (plain == null) {
            plain = MethodHandles.lazyUpdater(target, initializer, false)
                    .asType(methodType(Object.class, Object.class));
        }
        return plain;
    }

    @DontInline
    private synchronized MethodHandle initializeVolatile() {
        if (volatileUpdater == null) {
            volatileUpdater = MethodHandles.lazyUpdaterVolatile(target, initializer, false)
                    .asType(methodType(Object.class, Object.class));
        }
        return volatileUpdater;
    }

    @DontInline
    private synchronized MethodHandle initializeSynchronized() {
        if (synchronizedUpdater == null) {
            synchronizedUpdater = MethodHandles.lazyUpdaterSynchronized(target, initializer, false)
                    .asType(methodType(Object.class, Object.class, Object.class));
        }
        return synchronizedUpdater;
    }

}
