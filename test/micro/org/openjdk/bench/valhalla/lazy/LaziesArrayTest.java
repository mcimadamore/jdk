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
        return createHolders(state.configuration);
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

    private static Holder[] createHolders(Configuration configuration) {
        Holder[] holders = new Holder[HOLDER_COUNT];
        for (int i = 0; i < holders.length; i++) {
            holders[i] = configuration.create();
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
        public Configuration configuration;
    }

    @State(Scope.Thread)
    public static class ColdAccessState {
        @Param
        public Configuration configuration;

        private Holder[] holders;

        @Setup(Level.Invocation)
        public void setup() {
            holders = createHolders(configuration);
        }
    }

    @State(Scope.Thread)
    public static class HotAccessState {
        @Param
        public Configuration configuration;

        private Holder[] holders;

        @Setup(Level.Trial)
        public void setup() {
            holders = createHolders(configuration);
            for (Holder holder : holders) {
                for (int index = 0; index < ARRAY_SIZE; index++) {
                    holder.get(index);
                }
            }
        }
    }

    public enum Configuration {
        DIRECT(Variant.DIRECT, null),
        LAZY_ARRAY_CACHE_PLAIN(Variant.LAZY_ARRAY_CACHE, LazyMode.PLAIN),
        LAZY_ARRAY_CACHE_CAS(Variant.LAZY_ARRAY_CACHE, LazyMode.CAS),
        LAZY_ARRAY_CACHE_SYNCHRONIZED(Variant.LAZY_ARRAY_CACHE, LazyMode.SYNCHRONIZED),
        LAZY_ARRAY_USE_SITE_PLAIN(Variant.LAZY_ARRAY_USE_SITE, LazyMode.PLAIN),
        LAZY_ARRAY_USE_SITE_CAS(Variant.LAZY_ARRAY_USE_SITE, LazyMode.CAS),
        LAZY_ARRAY_USE_SITE_SYNCHRONIZED(Variant.LAZY_ARRAY_USE_SITE, LazyMode.SYNCHRONIZED),
        LAZY_ARRAY_DECL_SITE_PLAIN(Variant.LAZY_ARRAY_DECL_SITE, LazyMode.PLAIN),
        LAZY_ARRAY_DECL_SITE_CAS(Variant.LAZY_ARRAY_DECL_SITE, LazyMode.CAS),
        LAZY_ARRAY_DECL_SITE_SYNCHRONIZED(Variant.LAZY_ARRAY_DECL_SITE, LazyMode.SYNCHRONIZED),
        LAZY_LIST(Variant.LAZY_LIST, null);

        private final Variant variant;
        private final LazyMode mode;

        Configuration(Variant variant, LazyMode mode) {
            this.variant = variant;
            this.mode = mode;
        }

        Holder create() {
            return variant.create(mode);
        }
    }

    private enum Variant {
        DIRECT {
            @Override
            Holder create(LazyMode mode) {
                return new DirectHolder();
            }
        },
        LAZY_ARRAY_CACHE {
            @Override
            Holder create(LazyMode mode) {
                return switch (mode) {
                    case PLAIN -> new PlainCacheHolder();
                    case CAS -> new CasCacheHolder();
                    case SYNCHRONIZED -> new SynchronizedCacheHolder();
                };
            }
        },
        LAZY_ARRAY_USE_SITE {
            @Override
            Holder create(LazyMode mode) {
                return new UseSiteHolder(mode);
            }
        },
        LAZY_ARRAY_DECL_SITE {
            @Override
            Holder create(LazyMode mode) {
                return new DeclSiteHolder(mode);
            }
        },
        LAZY_LIST {
            @Override
            Holder create(LazyMode mode) {
                return new LazyListHolder();
            }
        };

        abstract Holder create(LazyMode mode);
    }

    public enum LazyMode {
        PLAIN,
        CAS,
        SYNCHRONIZED
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

    public abstract static class CacheHolder implements Holder {
        protected static final LazyArrayCache<List[], List<Integer>> CACHE =
                LazyArrayCache.of(List[].class,
                        (array, index) -> computeValues(index));

        @SuppressWarnings("unchecked")
        protected final List<Integer>[] values = (List<Integer>[]) new List<?>[ARRAY_SIZE];
    }

    public static final class PlainCacheHolder extends CacheHolder {
        @Override
        public List<Integer> get(int index) {
            return CACHE.get(values, index);
        }
    }

    public static final class CasCacheHolder extends CacheHolder {
        @Override
        public List<Integer> get(int index) {
            return CACHE.getVolatile(values, index);
        }
    }

    public static final class SynchronizedCacheHolder extends CacheHolder {
        @Override
        public List<Integer> get(int index) {
            return CACHE.getSynchronized(this, values, index);
        }
    }

    public static final class UseSiteHolder implements Holder {
        private final LazyArrayUseSite<List<Integer>> values;

        UseSiteHolder(LazyMode mode) {
            values = LazyArrayUseSite.of(ARRAY_SIZE, switch (mode) {
                case PLAIN -> LazyArrayUseSite.Policy.PLAIN;
                case CAS -> LazyArrayUseSite.Policy.CAS;
                case SYNCHRONIZED -> LazyArrayUseSite.Policy.ONCE;
            });
        }

        @Override
        public List<Integer> get(int index) {
            return values.get(this, index, UseSiteHolder::compute);
        }

        private List<Integer> compute(int index) {
            return computeValues(index);
        }
    }

    public static final class DeclSiteHolder implements Holder {
        private final LazyArrayDeclSite<DeclSiteHolder, List<Integer>> values;

        DeclSiteHolder(LazyMode mode) {
            values = LazyArrayDeclSite.of(ARRAY_SIZE, DeclSiteHolder::compute, switch (mode) {
                case PLAIN -> LazyArrayDeclSite.Policy.PLAIN;
                case CAS -> LazyArrayDeclSite.Policy.CAS;
                case SYNCHRONIZED -> LazyArrayDeclSite.Policy.ONCE;
            });
        }

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
