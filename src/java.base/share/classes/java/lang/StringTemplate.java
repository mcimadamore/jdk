/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatException;
import java.lang.invoke.StringConcatFactory;
import java.lang.invoke.VarHandle;
import java.lang.runtime.Carriers;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jdk.internal.access.JavaTemplateAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.ReferencedKeyMap;
import jdk.internal.vm.annotation.Stable;

/**
 * {@link StringTemplate} is the run-time representation of a string template or
 * text block template in a template expression.
 * <p>
 * In the source code of a Java program, a string template or text block template
 * contains an interleaved succession of <em>fragment literals</em> and <em>embedded
 * expressions</em>. The {@link StringTemplate#fragments()} method returns the
 * fragment literals, and the {@link StringTemplate#values()} method returns the
 * results of evaluating the embedded expressions. {@link StringTemplate} does not
 * provide access to the source code of the embedded expressions themselves; it is
 * not a compile-time representation of a string template or text block template.
 * <p>
 * {@link StringTemplate} is primarily used in conjunction with APIs
 * to produce a string or other meaningful value. Evaluation of a template expression
 * produces an instance of {@link StringTemplate}, with fragements and the values
 * of embedded expressions evaluated from left to right.
 * <p>
 * For example, the following code contains a template expression, which simply yields
 * a {@link StringTemplate}:
 * {@snippet lang=java :
 * int x = 10;
 * int y = 20;
 * StringTemplate st = "\{x} + \{y} = \{x + y}";
 * List<String> fragments = st.fragments();
 * List<Object> values = st.values();
 * }
 * {@code fragments} will be equivalent to {@code List.of("", " + ", " = ", "")},
 * which includes the empty first and last fragments. {@code values} will be the
 * equivalent of {@code List.of(10, 20, 30)}.
 * <p>
 * The following code contains a template expression with the same template but converting
 * to a string using the {@link #str()} method:
 * {@snippet lang=java :
 * int x = 10;
 * int y = 20;
 * String s = "\{x} + \{y} = \{x + y}".join();
 * }
 *
 * @since 21
 *
 * @jls 15.8.6 Process Template Expressions
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public final class StringTemplate extends Carriers.CarrierObject  {
    /**
     * StringTemplate shared data.
     */
    private final SharedData sharedData;

    /**
     * Private constructor as mixed carrier.
     *
     * @param primitiveCount  number of primitive slots required (bound at callsite)
     * @param objectCount     number of object slots required (bound at callsite)
     * @param sharedData      {@link StringTemplate} shared data
     */
    private StringTemplate(int primitiveCount, int objectCount, SharedData sharedData) {
        super(primitiveCount, objectCount);
        this.sharedData = sharedData;
    }

    /**
     * Private constructor as values.
     *
     * @param values      list of object values
     * @param sharedData  {@link StringTemplate} shared data
     */
    private StringTemplate(List<Object> values, SharedData sharedData) {
        super(values);
        this.sharedData = sharedData;
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
        try {
            return (List<Object>)sharedData.valuesMH().invokeExact(this);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException("string template values failure", ex);
        }
    }

    /**
     * Returns the string interpolation of the fragments and values for this
     * {@link StringTemplate}.
     * {@snippet lang = java:
     * String student = "Mary";
     * String teacher = "Johnson";
     * StringTemplate st = "The student \{student} is in \{teacher}'s classroom.";
     * String result = st.str(); // @highlight substring="join()"
     *}
     * In the above example, the value of  {@code result} will be
     * {@code "The student Mary is in Johnson's classroom."}. This is
     * produced by the interleaving concatenation of fragments and values from the supplied
     * {@link StringTemplate}. To accommodate concatenation, values are converted to strings
     * as if invoking {@link String#valueOf(Object)}.
     *
     * @return interpolation of this {@link StringTemplate}
     *
     * @implSpec The default implementation returns the result of invoking
     * {@code StringTemplate.join(this.fragments(), this.values())}.
     */
    private String str() {
        try {
            return (String)sharedData.joinMH().invokeExact(this);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException("string template join failure", ex);
        }
    }

    /**
     * Returns the string interpolation of the fragments and values for the specified
     * {@link StringTemplate}.
     * {@snippet lang = java:
     * String student = "Mary";
     * String teacher = "Johnson";
     * StringTemplate st = "The student \{student} is in \{teacher}'s classroom.";
     * String result = StringTemplate.str(st); // @highlight substring="join()"
     *}
     * In the above example, the value of  {@code result} will be
     * {@code "The student Mary is in Johnson's classroom."}. This is
     * produced by the interleaving concatenation of fragments and values from the supplied
     * {@link StringTemplate}. To accommodate concatenation, values are converted to strings
     * as if invoking {@link String#valueOf(Object)}.
     *
     * @param stringTemplate target {@link StringTemplate}
     * @return interpolation of this {@link StringTemplate}
     *
     * @throws NullPointerException if stringTemplate is null
     *
     * @implSpec The implementation returns the result of invoking {@code stringTemplate.join()}.
     */
    public static String str(StringTemplate stringTemplate) {
        Objects.requireNonNull(stringTemplate, "stringTemplate should not be null");
        return stringTemplate.str();
    }

    /**
     * Produces a diagnostic string that describes the fragments and values of this
     * {@link StringTemplate}.
     *
     * @return diagnostic string representing this string template
     *
     * @throws NullPointerException if stringTemplate is null
     */
    @Override
    public String toString() {
        return "StringTemplate{ fragments = [ \"" +
                String.join("\", \"", fragments()) +
                "\" ], values = " +
                values() +
                " }";
    }

    /**
     * Combine one or more {@link StringTemplate StringTemplates} to produce a combined {@link StringTemplate}.
     * {@snippet lang = java:
     * StringTemplate st = StringTemplate.combine("\{a}", "\{b}", "\{c}");
     * assert st.str().equals("\{a}\{b}\{c}");
     *}
     *
     * @param flatten  if true will flatten nested {@link StringTemplate StringTemplates} into the
     *                 combination
     * @param sts      zero or more {@link StringTemplate StringTemplates}
     *
     * @return combined {@link StringTemplate}
     *
     * @throws NullPointerException if sts is null or if any element of sts is null
     * @throws IllegalArgumentException if too many embedded expressions
     */
    private static StringTemplate combineST(boolean flatten, StringTemplate... sts) {
        Objects.requireNonNull(sts, "sts must not be null");
        if (sts.length == 0) {
            return Factory.createStringTemplate(List.of(""), List.of());
        } else if (sts.length == 1 && !flatten) {
            return Objects.requireNonNull(sts[0], "string templates should not be null");
        }
        List<String> fragments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (StringTemplate st : sts) {
            Objects.requireNonNull(st, "string templates should not be null");
            flattenST(flatten, st, fragments, values);
        }
        if (200 < values.size()) {
            throw new RuntimeException("string template combine too many expressions");
        }
        return Factory.createStringTemplate(fragments, values);
    }

    /**
     * Recursively combining the specified {@link StringTemplate} to the mix.
     *
     * @param flatten     if true will flatten nested {@link StringTemplate StringTemplates} into the
     *                    combination
     * @param st          specified {@link StringTemplate}
     * @param fragments   accumulation of fragments
     * @param getters     accumulation of getters
     */
    private static void flattenST(boolean flatten, StringTemplate st,
                                  List<String> fragments, List<Object> values) {
        Iterator<String> fragmentsIter = st.fragments().iterator();
        if (fragments.isEmpty()) {
            fragments.add(fragmentsIter.next());
        } else {
            int last = fragments.size() - 1;
            fragments.set(last, fragments.get(last) + fragmentsIter.next());
        }
        MethodType type = st.sharedData.type();
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

    /**
     * Combine zero or more {@link StringTemplate StringTemplates} into a single
     * {@link StringTemplate}.
     * {@snippet lang = java:
     * StringTemplate st = StringTemplate.combine(false, "\{a}", "\{b}", "\{c}");
     * assert st.str().equals("\{a}\{b}\{c}".join());
     *}
     * Fragment lists from the {@link StringTemplate StringTemplates} are combined end to
     * end with the last fragment from each {@link StringTemplate} concatenated with the
     * first fragment of the next. To demonstrate, if we were to take two strings and we
     * combined them as follows: {@snippet lang = "java":
     * String s1 = "abc";
     * String s2 = "xyz";
     * String sc = s1 + s2;
     * assert Objects.equals(sc, "abcxyz");
     * }
     * the last character {@code "c"} from the first string is juxtaposed with the first
     * character {@code "x"} of the second string. The same would be true of combining
     * {@link StringTemplate StringTemplates}.
     * {@snippet lang=java :
     * StringTemplate st1 = "a\{""}b\{""}c";
     * StringTemplate st2 = "x\{""}y\{""}z";
     * StringTemplate st3 = "a\{""}b\{""}cx\{""}y\{""}z";
     * StringTemplate stc = StringTemplate.combine(false, st1, st2);
     *
     * assert Objects.equals(st1.fragments(), List.of("a", "b", "c"));
     * assert Objects.equals(st2.fragments(), List.of("x", "y", "z"));
     * assert Objects.equals(st3.fragments(), List.of("a", "b", "cx", "y", "z"));
     * assert Objects.equals(stc.fragments(), List.of("a", "b", "cx", "y", "z"));
     * }
     * Values lists are simply concatenated to produce a single values list.
     * The result is a well-formed {@link StringTemplate} with n+1 fragments and n values, where
     * n is the total of number of values across all the supplied
     * {@link StringTemplate StringTemplates}.
     *
     * @param flatten          if true will flatten nested {@link StringTemplate StringTemplates} into the
     *                         combination
     * @param stringTemplates  zero or more {@link StringTemplate}
     *
     * @return combined {@link StringTemplate}
     *
     * @throws NullPointerException if stringTemplates is null or if any of the
     * {@code stringTemplates} are null
     *
     * @implNote If zero {@link StringTemplate} arguments are provided then a
     * {@link StringTemplate} with an empty fragment and no values is returned, as if invoking
     * <code>StringTemplate.of("")</code> . If only one {@link StringTemplate} argument is provided
     * then it is returned unchanged.
     */
    public static StringTemplate combine(boolean flatten, StringTemplate... stringTemplates) {
        return combineST(flatten, stringTemplates);
    }

    /**
     * Combine a list of {@link StringTemplate StringTemplates} into a single
     * {@link StringTemplate}.
     * {@snippet lang = java:
     * StringTemplate st = StringTemplate.combine(false, List.of("\{a}", "\{b}", "\{c}"));
     * assert st.str().equals("\{a}\{b}\{c}".join());
     *}
     * Fragment lists from the {@link StringTemplate StringTemplates} are combined end to
     * end with the last fragment from each {@link StringTemplate} concatenated with the
     * first fragment of the next. To demonstrate, if we were to take two strings and we
     * combined them as follows: {@snippet lang = "java":
     * String s1 = "abc";
     * String s2 = "xyz";
     * String sc = s1 + s2;
     * assert Objects.equals(sc, "abcxyz");
     * }
     * the last character {@code "c"} from the first string is juxtaposed with the first
     * character {@code "x"} of the second string. The same would be true of combining
     * {@link StringTemplate StringTemplates}.
     * {@snippet lang=java :
     * StringTemplate st1 = "a\{""}b\{""}c";
     * StringTemplate st2 = "x\{""}y\{""}z";
     * StringTemplate st3 = "a\{""}b\{""}cx\{""}y\{""}z";
     * StringTemplate stc = StringTemplate.combine(false, List.of(st1, st2));
     *
     * assert Objects.equals(st1.fragments(), List.of("a", "b", "c"));
     * assert Objects.equals(st2.fragments(), List.of("x", "y", "z"));
     * assert Objects.equals(st3.fragments(), List.of("a", "b", "cx", "y", "z"));
     * assert Objects.equals(stc.fragments(), List.of("a", "b", "cx", "y", "z"));
     * }
     * Values lists are simply concatenated to produce a single values list.
     * The result is a well-formed {@link StringTemplate} with n+1 fragments and n values, where
     * n is the total of number of values across all the supplied
     * {@link StringTemplate StringTemplates}.
     *
     * @param flatten          if true will flatten nested {@link StringTemplate StringTemplates} into the
     *                         combination
     * @param stringTemplates  list of {@link StringTemplate}
     *
     * @return combined {@link StringTemplate}
     *
     * @throws NullPointerException if stringTemplates is null or if any of the
     * its elements are null
     *
     * @implNote If {@code stringTemplates.size() == 0} then a {@link StringTemplate} with
     * an empty fragment and no values is returned, as if invoking
     * <code>StringTemplate.of("")</code> . If {@code stringTemplates.size() == 1}
     * then the first element of the list is returned unchanged.
     */
    public static StringTemplate combine(boolean flatten, List<StringTemplate> stringTemplates) {
        return combineST(flatten, stringTemplates.toArray(StringTemplate[]::new));
    }

    /**
     * Constructs a new {@link StringTemplate} using this instance's fragments and
     * values mapped from this instance's values by the specified
     * {@code mapper} function.
     * {@snippet lang=java :
     * StringTemplate st2 = st1.mapValue(v -> {
     *      if (v instanceof Supplier<?> s) {
     *          return s.get();
     *      }
     *      return v;
     * });
     * }
     *
     * @param mapper mapper function
     * @return new {@link StringTemplate}
     */
    public StringTemplate mapValues(Function<Object, Object> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<Object> values = values()
            .stream()
            .map(mapper)
            .toList();
        return Factory.createStringTemplate(fragments(), values);
    }

    /**
     * TBD
     * @param <T> TBD
     * @param owner TBD
     * @param supplier TBD
     * @return TBD
     */
    public <T> T getMetaData(Object owner, Supplier<T> supplier) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        return sharedData.getMetaData(owner, supplier);
    }

    /**
     * Test this {@link StringTemplate} against another {@link StringTemplate} for equality.
     *
     * @param other  other {@link StringTemplate}
     *
     * @return true if the {@link StringTemplate#fragments()} and {@link StringTemplate#values()}
     * of the two {@link StringTemplate StringTemplates} are equal.
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof StringTemplate that) {
            return Objects.equals(fragments(), that.fragments()) && super.equals(other);
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
        return 31 * Objects.hashCode(fragments()) + super.hashCode();
    }

    /**
     * StringTemplate shared data. Used to hold information for a {@link StringTemplate}
     * constructed at a specific {@link java.lang.invoke.CallSite CallSite}.
     */
    private static final class SharedData {
        /**
         * owner field {@link VarHandle}.
         */
        private static final VarHandle OWNER_VH;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                OWNER_VH = lookup.findVarHandle(SharedData.class, "owner", Object.class);
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
         * Specialized {@link MethodHandle} used to implement the {@link StringTemplate StringTemplate's}
         * {@code values} method. This {@link MethodHandle} is shared by all instances created at the
         * {@link java.lang.invoke.CallSite CallSite}.
         */
        @Stable
        private final MethodHandle valuesMH;

        /**
         * Specialized {@link MethodHandle} used to implement the {@link StringTemplate StringTemplate's}
         * {@code join} method. This {@link MethodHandle} is shared by all instances created at the
         * {@link java.lang.invoke.CallSite CallSite}.
         */
        @Stable
        private final MethodHandle joinMH;

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
         * @param valuesMH        {@link MethodHandle} to produce list of values
         * @param joinMH          {@link MethodHandle} to produce interpolation
         */
        SharedData(List<String> fragments, MethodType type, List<Class<?>> types,
                                 MethodHandle valuesMH, MethodHandle joinMH) {
            this.fragments = fragments;
            this.type = type;
            this.valuesMH = valuesMH;
            this.joinMH = joinMH;
            this.owner = null;
            this.metaData = null;

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
         * {@return MethodHandle to return list of values}
         */
        MethodHandle valuesMH() {
            return valuesMH;
        }

        /**
         * {@return MethodHandle to return string interpolation }
         */
        MethodHandle joinMH() {
            return joinMH;
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

    /**
     * This class synthesizes {@link StringTemplate StringTemplates} based on
     * fragments and bootstrap method type.
     *
     * @since 21
     *
     * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
     *          Do not rely on its availability.
     */
    private static final class Factory {

        /**
         * Private constructor.
         */
        Factory() {
            throw new AssertionError("private constructor");
        }

        /*
         * {@link StringTemplate} constructor MethodHandle.
         */
        private static final MethodHandle CONSTRUCTOR;

        /**
         * Nullable list constructing MethodHandle;
         */
        private static final MethodHandle TO_LIST;

        /**
         * Object to string, special casing {@link StringTemplate};
         */
        private static final MethodHandle OBJECT_TO_STRING;

        /**
         * {@link StringTemplate} to string using interpolation.
         */
        private static final MethodHandle TEMPLATE_TO_STRING;

        /*
         * Frequently used method types.
         */
        private static final MethodType JOIN_MT = MethodType.methodType(String.class, StringTemplate.class);
        private static final MethodType VALUES_MT = MethodType.methodType(List.class, StringTemplate.class);

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                MethodType mt = MethodType.methodType(void.class, int.class, int.class, SharedData.class);
                CONSTRUCTOR = lookup.findConstructor(StringTemplate.class, mt)
                    .asType(mt.changeReturnType(Carriers.CarrierObject.class));

                mt = MethodType.methodType(List.class, Object[].class);
                TO_LIST = lookup.findStatic(Factory.class, "toList", mt);

                mt = MethodType.methodType(String.class, Object.class);
                OBJECT_TO_STRING = lookup.findStatic(Factory.class, "objectToString", mt);

                mt = MethodType.methodType(String.class, StringTemplate.class);
                TEMPLATE_TO_STRING = lookup.findStatic(Factory.class, "templateToString", mt);
            } catch(ReflectiveOperationException ex) {
                throw new AssertionError("carrier static init fail", ex);
            }
        }

        /**
         * Create a new {@link StringTemplate} creating {@link MethodHandle}.
         *
         * @param fragments  string template fragments
         * @param type       value types with a StringTemplate return
         *
         * @return {@link MethodHandle} that can construct a {@link StringTemplate} with arguments
         * used as values.
         */
        private static MethodHandle createStringTemplateMH(List<String> fragments, MethodType type) {
            List<MethodHandle> components = Carriers.components(type);
            List<MethodHandle> getters = components.stream()
                .map(c -> c.asType(c.type().changeParameterType(0, StringTemplate.class)))
                .toList();
            List<Class<?>> ptypes = returnTypes(components);
            int[] permute = new int[ptypes.size()];
            MethodHandle valuesMH = createValuesMH(ptypes, getters, permute);
            List<MethodHandle> filters = filterGetters(getters);
            List<Class<?>> ftypes = returnTypes(filters);
            MethodHandle joinMH = createJoinMH(fragments, ftypes, filters, permute);
            SharedData sharedData = new SharedData(fragments, type, type.parameterList(), valuesMH, joinMH);
            MethodHandle constructor = createConstructorMH(type, sharedData, ptypes);
            return constructor;
        }

        /**
         * Create a new {@link StringTemplate} from values
         *
         * @param fragments  list of string fragments
         * @param values     lisy of object values
         *
         * @return new {@link StringTemplate}
         */
        private static StringTemplate createStringTemplate(List<String> fragments, List<Object> values) {
            SharedData sharedData = sharedDataMap.get(fragments);
            if (sharedData != null) {
                return new StringTemplate(values, sharedData);
            }
            Class<?>[] objectTypes = new Class<?>[values.size()];
            Arrays.fill(objectTypes, Object.class);
            MethodType type = MethodType.methodType(StringTemplate.class, objectTypes);
            List<MethodHandle> components = Carriers.components(type);
            List<MethodHandle> getters = components.stream()
                .map(c -> c.asType(c.type().changeParameterType(0, StringTemplate.class)))
                .toList();
            List<Class<?>> ptypes = returnTypes(components);
            int[] permute = new int[ptypes.size()];
            MethodHandle valuesMH = createValuesMH(ptypes, getters, permute);
            List<MethodHandle> filters = filterGetters(getters);
            List<Class<?>> ftypes = returnTypes(filters);
            MethodHandle joinMH = createJoinMH(fragments, ftypes, filters, permute);
            sharedData = new SharedData(fragments, type, type.parameterList(), valuesMH, joinMH);
            sharedDataMap.put(fragments, sharedData);
            return new StringTemplate(values, sharedData);
        }

        /**
         * Map to minimize redundant shared data.
         */
        private static final ReferencedKeyMap<List<String>, SharedData> sharedDataMap =
            ReferencedKeyMap.create(true, java.util.concurrent.ConcurrentHashMap::new);

        /**
         * Glean the return types from a list of {@link MethodHandle}.
         *
         * @param mhs  list of {@link MethodHandle}
         * @return list of return types
         */
        private static List<Class<?>> returnTypes(List<MethodHandle> mhs) {
            return mhs.stream()
                .map(mh -> mh.type().returnType())
                .collect(Collectors.toList());
        }

        /**
         * Create a get values list method {@link MethodHandle} for a {@link StringTemplate} with values
         * of ptypes.
         *
         * @param ptypes   list of value types
         * @param getters  list of value getter {@link MethodHandle}
         * @param permute  {@link StringTemplate} spreading permutation
         *
         * @return get values list {@link MethodHandle}
         */
        private static MethodHandle createValuesMH(List<Class<?>> ptypes, List<MethodHandle> getters, int[] permute) {
            MethodType valuesMT = MethodType.methodType(List.class, ptypes);
            MethodHandle valuesMH = TO_LIST.asCollector(Object[].class, getters.size()).asType(valuesMT);
            valuesMH = MethodHandles.filterArguments(valuesMH, 0, getters.toArray(MethodHandle[]::new));
            valuesMH = MethodHandles.permuteArguments(valuesMH, VALUES_MT, permute);
            return valuesMH;
        }

        /**
         * Create a join method {@link MethodHandle} for a {@link StringTemplate}.
         *
         * @param fragments  list of fragments
         * @param ptypes     list of value types
         * @param filters    list of value filters (flatten StringTemplates)
         * @param permute    {@link StringTemplate} spreading permutation
         *
         * @return join {@link MethodHandle}
         */
        private static MethodHandle createJoinMH(List<String> fragments, List<Class<?>> ptypes,
                                                 List<MethodHandle> filters, int[] permute) {
            try {
                MethodHandle joinMH = StringConcatFactory.makeConcatWithTemplate(fragments, ptypes);
                joinMH = MethodHandles.filterArguments(joinMH, 0, filters.toArray(MethodHandle[]::new));
                joinMH = MethodHandles.permuteArguments(joinMH, JOIN_MT, permute);
                return joinMH;
            } catch (StringConcatException ex) {
                throw new InternalError("constructing internal string template", ex);
            }
        }

        /**
         * Create a {@link StringTemplate} constructor.
         *
         * @param type        callsite {@link MethodType}
         * @param sharedData  {@link StringTemplate} shared data
         * @param ptypes      list of value types
         * @return
         */
        private static MethodHandle createConstructorMH(MethodType type, SharedData sharedData, List<Class<?>> ptypes) {
            MethodHandle constructor;
            constructor = MethodHandles.insertArguments(CONSTRUCTOR, 0,
                Carriers.primitiveCount(type), Carriers.objectCount(type), sharedData);
            constructor = MethodHandles.foldArguments(Carriers.initializerRaw(type), 0, constructor);
            MethodType constructorMT = MethodType.methodType(StringTemplate.class, ptypes);
            constructor = constructor.asType(constructorMT);
            return constructor;
        }

        /**
         * Interpolate nested {@link StringTemplate StringTemplates}.
         * @param getters {@link Carriers} component getters
         * @return getters filtered to translate {@link StringTemplate StringTemplates} to strings
         */
        private static List<MethodHandle> filterGetters(List<MethodHandle> getters) {
            List<MethodHandle> filters = new ArrayList<>();
            for (MethodHandle getter : getters) {
                Class<?> type = getter.type().returnType();
                if (type == StringTemplate.class) {
                    getter = MethodHandles.filterArguments(TEMPLATE_TO_STRING, 0, getter);
                } else if (type == Object.class) {
                    getter = MethodHandles.filterArguments(OBJECT_TO_STRING, 0, getter);
                }
                filters.add(getter);
            }
            return filters;
        }

        /**
         * Filter object for {@link StringTemplate} and convert to string, {@link String#valueOf(Object)} otherwise.
         * @param object object to filter
         * @return {@link StringTemplate} interpolation otherwise result of {@link String#valueOf(Object)}.
         */
        private static String objectToString(Object object) {
            if (object instanceof StringTemplate st) {
                return st.str();
            } else {
                return String.valueOf(object);
            }
        }

        /**
         * Filter {@link StringTemplate} to strings.
         * @param st {@link StringTemplate} to filter
         * @return {@link StringTemplate} interpolation otherwise "null"
         */
        private static String templateToString(StringTemplate st) {
            if (st != null) {
                return st.str();
            } else {
                return "null";
            }
        }

        /**
         * Collect nullable elements from an array into a unmodifiable list.
         * Elements are guaranteed to be safe.
         *
         * @param elements  elements to place in list
         *
         * @return unmodifiable list.
         */
        private static List<Object> toList(Object[] elements) {
            return Arrays.stream(elements).toList();
        }

    }
    /**
     * Manages string template bootstrap methods. These methods may be used,
     * by Java compiler implementations to create {@link StringTemplate} instances.
     * For example, the java compiler will translate the following code;
     * {@snippet lang=java :
     * int x = 10;
     * int y = 20;
     * StringTemplate st = "\{x} + \{y} = \{x + y}";
     * }
     * to byte code that invokes the {@link Runtime#newStringTemplate}
     * bootstrap method to construct a {@link CallSite} that accepts two integers
     * and produces a new {@link StringTemplate} instance.
     * {@snippet lang=java :
     * MethodHandles.Lookup lookup = MethodHandles.lookup();
     * MethodType mt = MethodType.methodType(StringTemplate.class, int.class, int.class);
     * CallSite cs = Runtime.newStringTemplate(lookup, "", mt, "", " + ", " = ", "");
     * ...
     * int x = 10;
     * int y = 20;
     * StringTemplate st = (StringTemplate)cs.getTarget().invokeExact(x, y);
     * }
     *
     * @since 21
     */
    @PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
    public static final class Runtime {
        /**
         * Private constructor.
         */
        private Runtime() {
            throw new AssertionError("private constructor");
        }

        /**
         * String template bootstrap method for creating string templates.
         * The static arguments include the fragments list.
         * The non-static arguments are the values.
         *
         * @param lookup          method lookup from call site
         * @param name            method name - not used
         * @param type            method type
         *                        (ptypes...) -> StringTemplate
         * @param fragments       fragment array for string template
         *
         * @return {@link CallSite} to handle create string template
         *
         * @throws NullPointerException if any of the arguments is null
         * @throws IllegalArgumentException if type does not return a {@link StringTemplate}
         */
        public static CallSite newStringTemplate(MethodHandles.Lookup lookup,
                                                 String name,
                                                 MethodType type,
                                                 String... fragments) {
            Objects.requireNonNull(lookup, "lookup is null");
            Objects.requireNonNull(name, "name is null");
            Objects.requireNonNull(type, "type is null");
            Objects.requireNonNull(fragments, "fragments is null");
            if (type.returnType() != StringTemplate.class) {
                throw new IllegalArgumentException("type must be of type StringTemplate");
            }

            MethodHandle mh = Factory.createStringTemplateMH(List.of(fragments), type).asType(type);

            return new ConstantCallSite(mh);
        }
    }

    /**
     * This class provides runtime support for string templates. The methods within
     * are intended for internal use only.
     *
     * @since 21
     *
     * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
     *          Do not rely on its availability.
     */
    private static final class Support implements JavaTemplateAccess {

        /**
         * Private constructor.
         */
        private Support() {
        }

        static {
            SharedSecrets.setJavaTemplateAccess(new Support());
        }

        @Override
        public List<Class<?>> getTypes(StringTemplate st) {
            Objects.requireNonNull(st, "st must not be null");
            return st.sharedData.type().parameterList();
        }

        @Override
        public <T> T getMetaData(StringTemplate st, Object owner, Supplier<T> supplier) {
            Objects.requireNonNull(st, "st must not be null");
            Objects.requireNonNull(owner, "owner must not be null");
            Objects.requireNonNull(supplier, "supplier must not be null");
            return st.sharedData.getMetaData(owner, supplier);
        }

        @Override
        public MethodHandle bindTo(StringTemplate st, MethodHandle mh) {
            Objects.requireNonNull(st, "st must not be null");
            Objects.requireNonNull(mh, "mh must not be null");
            MethodHandle[] components = Carriers.componentsRaw(st.sharedData.type()).toArray(MethodHandle[]::new);
            int[] permute = new int[components.length];
            mh = MethodHandles.filterArguments(mh, 0, components);
            MethodType mt = MethodType.methodType(mh.type()
                .returnType(), Carriers.CarrierObject.class);
            mh = MethodHandles.permuteArguments(mh, mt, permute);
            mt = mt.changeParameterType(0, StringTemplate.class);
            return mh.asType(mt);
        }

    }

}
