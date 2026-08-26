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
package jdk.internal.classfile.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Function;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.TrustFinalFields;

/** A write-once, lazily-computed field value used by scalar classfile caches. */
@TrustFinalFields
final class LazyUpdater<R, X> {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private final Object staticBase;
    private final long fieldOffset;
    private final boolean isStatic;

    static <R, X> LazyUpdater<R, X> ofInstance(Class<R> declaringClass,
                                                 String fieldName,
                                                 Class<?> fieldType) {
        return ofInstance(declaringClass, fieldName, fieldType, MethodHandles.publicLookup());
    }

    static <R, X> LazyUpdater<R, X> ofInstance(Class<R> declaringClass,
                                                 String fieldName,
                                                 Class<?> fieldType,
                                                 MethodHandles.Lookup lookup) {
        return new LazyUpdater<>(declaringClass, fieldName, fieldType, lookup, false);
    }

    static <X> LazyUpdater<Void, X> ofStatic(Class<?> declaringClass,
                                               String fieldName,
                                               Class<?> fieldType) {
        return ofStatic(declaringClass, fieldName, fieldType, MethodHandles.publicLookup());
    }

    static <X> LazyUpdater<Void, X> ofStatic(Class<?> declaringClass,
                                               String fieldName,
                                               Class<?> fieldType,
                                               MethodHandles.Lookup lookup) {
        return new LazyUpdater<>(declaringClass, fieldName, fieldType, lookup, true);
    }

    private LazyUpdater(Class<?> declaringClass, String fieldName, Class<?> fieldType,
                        MethodHandles.Lookup lookup, boolean expectedStatic) {
        try {
            var field = declaringClass.getDeclaredField(fieldName);
            isStatic = Modifier.isStatic(field.getModifiers());
            if (isStatic != expectedStatic) {
                throw new IllegalArgumentException(fieldName + " is not " + (expectedStatic ? "static" : "an instance field"));
            }
            if (isStatic) {
                lookup.findStaticVarHandle(declaringClass, fieldName, fieldType);
                staticBase = UNSAFE.staticFieldBase(field);
                fieldOffset = UNSAFE.staticFieldOffset(field);
            } else {
                lookup.findVarHandle(declaringClass, fieldName, fieldType);
                staticBase = null;
                fieldOffset = UNSAFE.objectFieldOffset(declaringClass, fieldName);
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalArgumentException("Cannot access " + declaringClass.getName() + "." + fieldName, ex);
        }
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    X getOrCompute(R receiver, Function<? super R, ? extends X> computer) {
        Object base = isStatic ? staticBase : receiver;
        X result = (X) UNSAFE.getReferenceAcquire(base, fieldOffset);
        if (result != null) {
            return result;
        }
        X candidate = Objects.requireNonNull(computer.apply(receiver));
        X witness = (X) UNSAFE.compareAndExchangeReference(base, fieldOffset, null, candidate);
        return witness == null ? candidate : witness;
    }
}
