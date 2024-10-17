/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.template;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.vm.annotation.Stable;

public final class StringTemplateImpl implements StringTemplate {

    final SharedData sharedData;
    final Object[] values;

    static MethodHandle GET_VALUE;

    static {
        try {
            GET_VALUE = MethodHandles.lookup().findStatic(StringTemplateImpl.class, "getValue",
                    MethodType.methodType(Object.class, StringTemplate.class, int.class));
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public StringTemplateImpl(SharedData sharedData, Object... values) {
        this.sharedData = sharedData;
        this.values = values;
    }

    @Override
    public List<String> fragments() {
        return sharedData.fragments();
    }

    @Override
    public List<Parameter> parameters() {
        return sharedData.parameters();
    }

    @Override
    public List<Object> values() {
        // some values might be null, so can't use List::of
        return Arrays.asList(values);
    }

    @Override
    public String toString() {
        return "StringTemplate{ fragments = [ \"" +
                String.join("\", \"", fragments()) +
                "\" ], parameters = " +
                parameters() +
                "\" ], values = " +
                values() +
                " }";
    }

    @Override
    public StringTemplate mapValues(Function<Object, Object> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Object[] values = values()
                .stream()
                .map(mapper)
                .toArray();
        return sharedData.makeStringTemplateFromValues(values);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof StringTemplate that) {
            return fragments().equals(that.fragments()) &&
                    values().equals(that.values());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * fragments().hashCode() + values().hashCode();
    }

    // support

    static MethodHandle getter(int index, Class<?> ptype) {
        return MethodHandles.insertArguments(GET_VALUE, 1, index)
                .asType(MethodType.methodType(ptype, StringTemplate.class));
    }

    static Object getValue(StringTemplate st, int index) {
        return ((StringTemplateImpl)st).values[index];
    }

    public List<Class<?>> getTypes() {
        return sharedData.type().parameterList();
    }

    public <T> T getMetaData(Object owner, Supplier<T> supplier) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        return sharedData.getMetaData(owner, supplier);
    }

    public MethodHandle bindTo(MethodHandle mh) {
        Objects.requireNonNull(mh, "mh must not be null");
        MethodHandle[] getters = new MethodHandle[sharedData.type.parameterCount()];
        for (int i = 0 ; i < getters.length ; i++) {
            getters[i] = StringTemplateImpl.getter(i, sharedData.type.parameterType(i));
        }
        int[] permute = new int[getters.length];
        mh = MethodHandles.filterArguments(mh, 0, getters);
        MethodType mt = MethodType.methodType(mh.type()
                .returnType(), StringTemplate.class);
        mh = MethodHandles.permuteArguments(mh, mt, permute);
        return mh.asType(mt);
    }

    /**
     * StringTemplate shared data. Used to hold information for a {@link StringTemplate}
     * constructed at a specific {@link java.lang.invoke.CallSite CallSite}.
     */
    public static final class SharedData {
        /**
         * owner field {@link VarHandle}.
         */
        private static final VarHandle OWNER_VH;

        /**
         * StringTemplate factory.
         */
        private static final MethodHandle FACTORY;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                OWNER_VH = lookup.findVarHandle(SharedData.class, "owner", Object.class);
                FACTORY = lookup.findVirtual(SharedData.class, "makeStringTemplateFromValues",
                        MethodType.methodType(StringTemplate.class, Object[].class));
            } catch (ReflectiveOperationException ex) {
                throw new InternalError(ex);
            }
        }

        /**
         * List of string fragments for the string template. This value of this list is shared by
         * all instances created at the {@link java.lang.invoke.CallSite CallSite}.
         */
        @Stable
        private final List<String> fragments;

        @Stable
        private final List<Parameter> parameters;

        @Stable
        private final MethodType type;

        /**
         * Owner of metadata. Metadata is used to cache information at a
         * {@link java.lang.invoke.CallSite CallSite} by a processor. Only one
         * cache is available, first processor to attempt wins. This is under the assumption
         * that each {@link StringTemplate} serves one purpose. A processor should
         * have a fallback if it does not win the cache.
         */
        @Stable
        private Object owner;

        /**
         *  Metadata cache.
         */
        @Stable
        private Object metaData;

        /**
         * Constructor. Contents are bound to the {@link java.lang.invoke.CallSite CallSite}.
         * @param fragments       list of string fragments
         * @param parameters      list of string template parameters
         * @param type            {@link MethodType} at callsite
         */
        public SharedData(List<String> fragments, List<Parameter> parameters, MethodType type) {
            this.fragments = fragments;
            this.parameters = parameters;
            this.type = type;
            this.owner = null;
            this.metaData = null;
        }

        public StringTemplate makeStringTemplateFromValues(Object... args) {
            return new StringTemplateImpl(this, args);
        }

        public MethodHandle factoryHandle() {
            return FACTORY.bindTo(this)
                    .asCollector(Object[].class, type.parameterCount())
                    .asType(type);
        }

        /**
         * {@return list of string fragments}
         */
        List<String> fragments() {
            return fragments;
        }

        /**
         * {@return list of string fragments}
         */
        List<Parameter> parameters() {
            return parameters;
        }

        /**
         * {@return callsite {@link MethodType} }
         */
        MethodType type() {
            return type;
        }

        /**
         * Get owner meta data.
         *
         * @param owner     owner object, should be unique to the processor
         * @param supplier  supplier of meta data
         * @return meta data or null if it fails to win the cache
         */
        @SuppressWarnings("unchecked")
        <S, T> T getMetaData(S owner, Supplier<T> supplier) {
            if (this.owner == null && (Object)OWNER_VH.compareAndExchange(this, null, owner) == null) {
                metaData = supplier.get();
            }
            return this.owner == owner ? (T)metaData : null;
        }

    }

    public record ParameterImpl(Class<?> type, List<Annotation> annotations) implements Parameter {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return (T)annotations.stream()
                    .filter(a -> a.annotationType().equals(annotationClass))
                    .findFirst().orElse(null);
        }

        @Override
        public Annotation[] getAnnotations() {
            return annotations.toArray(Annotation[]::new);
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return annotations.toArray(Annotation[]::new);
        }
    }
}
