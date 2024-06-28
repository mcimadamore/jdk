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

package jdk.internal.template;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.StringConcatException;
import java.lang.invoke.StringConcatFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * This class provides a model for associating metadata by an owning class with a literal
 * StringTemplate.
 */
public class StringTemplateMetaData {

    /**
     * Per {@link StringTemplate} {@link MethodHandle}.
     */
    @Stable
    private MethodHandle methodHandle;

    /**
     * Public constructor.
     */
    public StringTemplateMetaData() {
        this.methodHandle = null;
    }

    /**
     * {@return the method handle}
     */
    public MethodHandle methodHandle() {
        return methodHandle;
    }

    /**
     * Set the method handle.
     *
     * @param methodHandle the method handle to set
     */
    public void setMethodHandle(MethodHandle methodHandle) {
        this.methodHandle = methodHandle;
    }

    /**
     * Construct the {@link MethodHandle}.  Override if special handling is required.
     * @param st  associated {@link StringTemplate}
     * @return constructed {@link MethodHandle}
     */
    public MethodHandle createMethodHandle(StringTemplate st) {
        Objects.requireNonNull(st, "st must not be null");
        List<String> fragments = filterFragments(st.fragments());
        List<Class<?>> ptypes = ((StringTemplateImpl)st).getTypes();
        MethodHandle[] filters = createValueFilters(ptypes);
        List<Class<?>> fTypes = new ArrayList<>();
        for (int i = 0; i < filters.length; i++) {
            if (filters[i] == null) {
                fTypes.add(ptypes.get(i));
            } else {
                fTypes.add(filters[i].type().returnType());
            }
        }
        MethodHandle mh = createConcat(fragments, fTypes);
        mh = ((StringTemplateImpl)st).bindTo(mh);
        return mh;
    }

    /**
     * Filter the associated {@link StringTemplate StringTemplate's} fragments. Override
     * if special handling is required.
     *
     * @param fragments  associated {@link StringTemplate StringTemplate's} fragments
     *
     * @return filtered fragments
     */
    public List<String> filterFragments(List<String> fragments) {
        Objects.requireNonNull(fragments, "fragments must not be null");
        return fragments;
    }

    /**
     * Create value filters for associated {@link StringTemplate StringTemplate's} values.
     * Override if special handling is required.
     *
     * @param ptypes  value types
     *
     * @return array of {@link MethodHandle} containing filters for each value or null if
     * a value needs no filtering.
     */
    public MethodHandle[] createValueFilters(List<Class<?>> ptypes) {
        Objects.requireNonNull(ptypes, "ptypes must not be null");
        return new MethodHandle[ptypes.size()];
    }

    /**
     * Create the concat {@link MethodHandle}.
     *
     * @param fragments  concat fragments
     * @param ptypes     concat value types
     *
     * @return {@link MethodHandle} that performs the basic concat.
     */
    public MethodHandle createConcat(List<String> fragments, List<Class<?>> ptypes) {
        Objects.requireNonNull(fragments, "fragments must not be null");
        Objects.requireNonNull(ptypes, "ptypes must not be null");
        MethodHandle mh;
        try {
            mh = StringConcatFactory.makeConcatWithTemplate(fragments, ptypes);
        } catch (StringConcatException e) {
            throw new InternalError(e);
        }
        return mh;
    }

    /**
     * invoke the constructed {@link MethodHandle} to return the processor's string result.
     * May return null if
     *
     * @param st  associated {@link StringTemplate}
     * @return the processor's string result
     */
    public String invoke(StringTemplate st) {
        Objects.requireNonNull(st, "st must not be null");
        if (methodHandle != null) {
            try {
                return (String)methodHandle.invokeExact(st);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }
        return null;
    }

    /**
     * Method to set up the metadata. Returns null if the {@link StringTemplate} has a
     * different owner.
     *
     * @param st        associated {@link StringTemplate}
     * @param owner     metadata owner
     * @param supplier  supplier to generate instance of metadata
     *
     * @return instance of the metadata class.
     */
    public static <M extends StringTemplateMetaData> M getMetaData(StringTemplate st, Object owner, Supplier<M> supplier) {
        Objects.requireNonNull(st, "st must not be null");
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        M metaData = ((StringTemplateImpl)st).getMetaData(owner, () -> {
            M md = supplier.get();
            md.setMethodHandle(md.createMethodHandle(st));
            return md;
        });
        return metaData;
    }
}
