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

package java.lang;

import java.lang.annotation.Annotation;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.template.StringTemplateHelper;
import jdk.internal.template.StringTemplateImpl;
import jdk.internal.template.StringTemplateImpl.SharedData;

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
 * produces an instance of {@link StringTemplate}, with fragments and the values
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
 * to a string using the {@link StringTemplate#str(StringTemplate)} method:
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
public interface StringTemplate {

    /**
     * {@return a new string template with given fragments and values}
     * @param fragments string template fragments
     * @param values string template values
     */
    static StringTemplate of(List<String> fragments, List<Object> values) {
        MethodType mt = MethodType.methodType(StringTemplate.class)
                .appendParameterTypes(values.stream().map(Object::getClass).toArray(Class[]::new));
        return new SharedData(fragments, List.of(), mt).makeStringTemplateFromValues(values);
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
    List<String> fragments();

    /**
     * {@return the annotations associated with the arguments in this string template}
     */
    List<List<Annotation>> annotations();

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
    List<Object> values();

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
    static String str(StringTemplate stringTemplate) {
        Objects.requireNonNull(stringTemplate, "stringTemplate should not be null");
        return StringTemplateHelper.join(stringTemplate);
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
    String toString();

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
    static StringTemplate combine(boolean flatten, StringTemplate... stringTemplates) {
        return StringTemplateHelper.combineST(flatten, stringTemplates);
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
    static StringTemplate combine(boolean flatten, List<StringTemplate> stringTemplates) {
        return StringTemplateHelper.combineST(flatten, stringTemplates.toArray(StringTemplate[]::new));
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
    StringTemplate mapValues(Function<Object, Object> mapper);

    /**
     * Test this {@link StringTemplate} against another {@link StringTemplate} for equality.
     *
     * @param other  other {@link StringTemplate}
     *
     * @return true if the {@link StringTemplate#fragments()} and {@link StringTemplate#values()}
     * of the two {@link StringTemplate StringTemplates} are equal.
     */
    @Override
    boolean equals(Object other);

    /**
     * Return a hashCode that derived from this {@link StringTemplate StringTemplate's}
     * fragments and values.
     *
     * @return a hash code for a sequences of fragments and values
     */
    @Override
    int hashCode();

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
    final class Runtime {
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
                                                 Annotation[][] annotations,
                                                 String... fragments) {
            Objects.requireNonNull(lookup, "lookup is null");
            Objects.requireNonNull(name, "name is null");
            Objects.requireNonNull(type, "type is null");
            Objects.requireNonNull(fragments, "fragments is null");
            if (type.returnType() != StringTemplate.class) {
                throw new IllegalArgumentException("type must be of type StringTemplate");
            }
            MethodHandle mh = new SharedData(
                    List.of(fragments),
                    Stream.of(annotations).map(List::of).toList(),
                    type).factoryHandle();
            return new ConstantCallSite(mh);
        }
    }
}
