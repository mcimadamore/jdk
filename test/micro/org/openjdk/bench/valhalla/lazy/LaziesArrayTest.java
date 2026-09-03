/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package org.openjdk.bench.valhalla.lazy;

import java.lang.invoke.LazyArrayCache;
import java.lang.invoke.LazyArrayDeclSite;
import java.lang.invoke.LazyArrayUseSite;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@Fork(jvmArgs = "--enable-preview")
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class LaziesArrayTest {
    private static final int HOLDER_COUNT = 1024;
    private static final int ARRAY_SIZE = 16;
    private static final int VALUE_COUNT = 16;
    private static final int ELEMENT_COUNT = HOLDER_COUNT * ARRAY_SIZE;

    @Benchmark
    @OperationsPerInvocation(HOLDER_COUNT)
    public Holder[] allocate(AllocateState state) {
        return createHolders(state.variant);
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public void coldAccess(ColdAccessState state, Blackhole bh) {
        accessAll(state.holders, bh);
    }

    @Benchmark
    @OperationsPerInvocation(ELEMENT_COUNT)
    public void hotAccess(HotAccessState state, Blackhole bh) {
        accessAll(state.holders, bh);
    }

    private static void accessAll(Holder[] holders, Blackhole bh) {
        for (Holder holder : holders) {
            for (int index = 0; index < ARRAY_SIZE; index++) {
                bh.consume(holder.get(index).size());
            }
        }
    }

    private static Holder[] createHolders(Variant variant) {
        Holder[] holders = new Holder[HOLDER_COUNT];
        for (int i = 0; i < holders.length; i++) {
            holders[i] = variant.create();
        }
        return holders;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static List<Integer> computeValues(int index) {
        SplittableRandom random = new SplittableRandom(index);
        ArrayList<Integer> values = new ArrayList<>(VALUE_COUNT);
        for (int i = 0; i < VALUE_COUNT; i++) {
            values.add(random.nextInt());
        }
        return values;
    }

    @State(Scope.Thread)
    public static class AllocateState {
        @Param
        public Variant variant;
    }

    @State(Scope.Thread)
    public static class ColdAccessState {
        @Param
        public Variant variant;

        private Holder[] holders;

        @Setup(Level.Invocation)
        public void setup() {
            holders = createHolders(variant);
        }
    }

    @State(Scope.Thread)
    public static class HotAccessState {
        @Param
        public Variant variant;

        private Holder[] holders;

        @Setup(Level.Trial)
        public void setup() {
            holders = createHolders(variant);
            for (Holder holder : holders) {
                for (int index = 0; index < ARRAY_SIZE; index++) {
                    holder.get(index);
                }
            }
        }
    }

    public enum Variant {
        DIRECT {
            @Override
            Holder create() {
                return new DirectHolder();
            }
        },
        LAZY_ARRAY_CACHE {
            @Override
            Holder create() {
                return new CacheHolder();
            }
        },
        LAZY_ARRAY_USE_SITE {
            @Override
            Holder create() {
                return new UseSiteHolder();
            }
        },
        LAZY_ARRAY_DECL_SITE {
            @Override
            Holder create() {
                return new DeclSiteHolder();
            }
        },
        LAZY_LIST {
            @Override
            Holder create() {
                return new LazyListHolder();
            }
        };

        abstract Holder create();
    }

    public interface Holder {
        List<Integer> get(int index);
    }

    public static final class DirectHolder implements Holder {
        @SuppressWarnings("unchecked")
        private final List<Integer>[] values = (List<Integer>[]) new List<?>[ARRAY_SIZE];

        @Override
        public List<Integer> get(int index) {
            List<Integer> value = values[index];
            if (value == null) {
                values[index] = value = computeValues(index);
            }
            return value;
        }
    }

    public static final class CacheHolder implements Holder {
        private static final LazyArrayCache<List[], List<Integer>> CACHE =
                LazyArrayCache.of(List[].class,
                        (array, index) -> computeValues(index));

        @SuppressWarnings("unchecked")
        private final List<Integer>[] values = (List<Integer>[]) new List<?>[ARRAY_SIZE];

        @Override
        public List<Integer> get(int index) {
            return CACHE.get(values, index);
        }
    }

    public static final class UseSiteHolder implements Holder {
        private final LazyArrayUseSite<List<Integer>> values =
                LazyArrayUseSite.of(ARRAY_SIZE, LazyArrayUseSite.Policy.PLAIN);

        @Override
        public List<Integer> get(int index) {
            return values.get(this, index, UseSiteHolder::compute);
        }

        private List<Integer> compute(int index) {
            return computeValues(index);
        }
    }

    public static final class DeclSiteHolder implements Holder {
        private final LazyArrayDeclSite<DeclSiteHolder, List<Integer>> values =
                LazyArrayDeclSite.of(ARRAY_SIZE, DeclSiteHolder::compute,
                        LazyArrayDeclSite.Policy.PLAIN);

        @Override
        public List<Integer> get(int index) {
            return values.get(this, index);
        }

        private List<Integer> compute(int index) {
            return computeValues(index);
        }
    }

    public static final class LazyListHolder implements Holder {
        private final List<List<Integer>> values =
                List.ofLazy(ARRAY_SIZE, LaziesArrayTest::computeValues);

        @Override
        public List<Integer> get(int index) {
            return values.get(index);
        }
    }
}
