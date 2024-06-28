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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.vm.annotation.Stable;

public final class StringTemplateImpl implements StringTemplate {
    /**
     * StringTemplate shared data.
     */
    private final SharedData sharedData;
    private final Object[] values;

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

    static MethodHandle getter(int index, Class<?> ptype) {
        return MethodHandles.insertArguments(GET_VALUE, 1, index)
                .asType(MethodType.methodType(ptype, StringTemplate.class));
    }

    static Object getValue(StringTemplate st, int index) {
        return ((StringTemplateImpl)st).values[index];
    }

    /**
     * Returns a list of fragment literals for this {@link StringTemplate}.
     * The fragment literals are the character sequences preceding each of the embedded
     * expressions in source code, plus the character sequence following the last
     * embedded expression. Such character sequences may be zero-length if an embedded
     * expression appears at the beginning or end of a template, or if two embedded
     * expressions are directly adjacent in a template.
     * In the example: {@snippet lang=java :
     * String student = "Mary";
     * String teacher = "Johnson";
     * StringTemplate st = "The student \{student} is in \{teacher}'s classroom.";
     * List<String> fragments = st.fragments(); // @highlight substring="fragments()"
     * }
     * {@code fragments} will be equivalent to
     * {@code List.of("The student ", " is in ", "'s classroom.")}
     *
     * @return list of string fragments
     *
     * @implSpec the list returned is immutable
     */
    public List<String> fragments() {
        return sharedData.fragments();
    }

    /**
     * Returns a list of embedded expression results for this {@link StringTemplate}.
     * In the example:
     * {@snippet lang=java :
     * String student = "Mary";
     * String teacher = "Johnson";
     * StringTemplate st = "The student \{student} is in \{teacher}'s classroom.";
     * List<Object> values = st.values(); // @highlight substring="values()"
     * }
     * {@code values} will be equivalent to {@code List.of(student, teacher)}
     *
     * @return list of expression values
     *
     * @implSpec the list returned is immutable
     */
    public List<Object> values() {
        // some values might be null, so can't use List::of
        return Arrays.asList(values);
    }

    public String str() {
        MethodHandle joinMH = getMetaData(StringTemplateJoiner.class,
                () -> StringTemplateJoiner.makeJoinMH(sharedData.type, sharedData.fragments));
        if (joinMH != null) {
            try {
                return (String)joinMH.invokeExact((StringTemplate)this);
            } catch (Throwable ex) {
                throw new InternalError(ex);
            }
        } else {
            return StringTemplateJoiner.join(this);
        }
    }

    @Override
    public String toString() {
        return "StringTemplate{ fragments = [ \"" +
                String.join("\", \"", fragments()) +
                "\" ], values = " +
                values() +
                " }";
    }

    public static StringTemplate combineST(boolean flatten, StringTemplate... sts) {
        Objects.requireNonNull(sts, "sts must not be null");
        if (sts.length == 0) {
            return new SharedData(List.of(""), MethodType.methodType(StringTemplate.class)).makeStringTemplateFromValues();
        } else if (sts.length == 1 && !flatten) {
            return Objects.requireNonNull(sts[0], "string templates should not be null");
        }
        MethodType type = MethodType.methodType(StringTemplate.class);
        List<String> fragments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (StringTemplate st : sts) {
            type = type.appendParameterTypes(((StringTemplateImpl)st).sharedData.type.parameterArray());
            if (flatten) {
                for (int i = 0 ; i < type.parameterCount() ; i++) {
                    if (StringTemplate.class.isAssignableFrom(type.parameterType(i))) {
                        type = type.changeParameterType(i, String.class);
                    }
                }
            }
            Objects.requireNonNull(st, "string templates should not be null");
            flattenST(flatten, st, fragments, values);
        }
        if (200 < values.size()) {
            throw new RuntimeException("string template combine too many expressions");
        }
        return new SharedData(fragments, type).makeStringTemplateFromValues(values.toArray());
    }

    /**
     * Recursively combining the specified {@link StringTemplate} to the mix.
     *
     * @param flatten     if true will flatten nested {@link StringTemplate StringTemplates} into the
     *                    combination
     * @param st          specified {@link StringTemplate}
     * @param fragments   accumulation of fragments
     */
    public static void flattenST(boolean flatten, StringTemplate st,
                                  List<String> fragments, List<Object> values) {
        Iterator<String> fragmentsIter = st.fragments().iterator();
        if (fragments.isEmpty()) {
            fragments.add(fragmentsIter.next());
        } else {
            int last = fragments.size() - 1;
            fragments.set(last, fragments.get(last) + fragmentsIter.next());
        }
        MethodType type = ((StringTemplateImpl)st).sharedData.type();
        for(Object value : st.values()) {
            if (flatten && value instanceof StringTemplate nested) {
                flattenST(true, nested, fragments, values);
                int last = fragments.size() - 1;
                fragments.set(last, fragments.get(last) + fragmentsIter.next());
            } else {
                values.add(value);
                fragments.add(fragmentsIter.next());
            }
        }
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

    /**
     * Return a hashCode that derived from this {@link StringTemplate StringTemplate's}
     * fragments and values.
     *
     * @return a hash code for a sequences of fragments and values
     */
    @Override
    public int hashCode() {
        return 31 * fragments().hashCode() + values().hashCode();
    }

    // support

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

        /**
         * {@link MethodType} at callsite
         */
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
         * @param type            {@link MethodType} at callsite
         */
        public SharedData(List<String> fragments, MethodType type) {
            this.fragments = fragments;
            this.type = type;
            this.owner = null;
            this.metaData = null;
        }

        private StringTemplate makeStringTemplateFromValues(Object... args) {
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
}
