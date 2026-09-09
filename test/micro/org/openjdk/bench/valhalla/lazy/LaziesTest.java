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

import java.lang.LazyConstant;
import java.lang.invoke.LazyCacheDeclSite;
import java.lang.invoke.LazyValueDeclSite;
import java.lang.invoke.LazyValueUseSite;
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
@Fork(jvmArgs = {
        "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--enable-preview"
})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class LaziesTest {
    private static final int HOLDER_COUNT = 1024;
    private static final int VALUE_COUNT = 16;

    @Benchmark
    @OperationsPerInvocation(HOLDER_COUNT)
    public Holder[] allocate(CreateState state) {
        return createHolders(state.configuration);
    }

    @Benchmark
    @OperationsPerInvocation(HOLDER_COUNT)
    public void coldAccess(ColdAccessState state, Blackhole bh) {
        for (Holder holder : state.holders) {
            bh.consume(holder.get().size());
        }
    }

    @Benchmark
    @OperationsPerInvocation(HOLDER_COUNT)
    public void hotAccess(HotAccessState state, Blackhole bh) {
        for (Holder holder : state.holders) {
            bh.consume(holder.get().size());
        }
    }

    private static Holder[] createHolders(Configuration configuration) {
        Holder[] holders = new Holder[HOLDER_COUNT];
        for (int i = 0; i < holders.length; i++) {
            holders[i] = configuration.create(i);
        }
        return holders;
    }

    @State(Scope.Thread)
    public static class CreateState {
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
                holder.get();
            }
        }
    }

    public enum Configuration {
        DIRECT(Variant.DIRECT, null),
        CACHED_METHOD(Variant.CACHED_METHOD, null),
        LAZY_CACHE_DECL_SITE_PLAIN(Variant.LAZY_CACHE_DECL_SITE, LazyMode.PLAIN),
        LAZY_CACHE_DECL_SITE_CAS(Variant.LAZY_CACHE_DECL_SITE, LazyMode.CAS),
        LAZY_CACHE_DECL_SITE_SYNCHRONIZED(Variant.LAZY_CACHE_DECL_SITE, LazyMode.SYNCHRONIZED),
        LAZY_VALUE_USE_SITE_PLAIN(Variant.LAZY_VALUE_USE_SITE, LazyMode.PLAIN),
        LAZY_VALUE_USE_SITE_CAS(Variant.LAZY_VALUE_USE_SITE, LazyMode.CAS),
        LAZY_VALUE_USE_SITE_SYNCHRONIZED(Variant.LAZY_VALUE_USE_SITE, LazyMode.SYNCHRONIZED),
        LAZY_VALUE_DECL_SITE_PLAIN(Variant.LAZY_VALUE_DECL_SITE, LazyMode.PLAIN),
        LAZY_VALUE_DECL_SITE_CAS(Variant.LAZY_VALUE_DECL_SITE, LazyMode.CAS),
        LAZY_VALUE_DECL_SITE_SYNCHRONIZED(Variant.LAZY_VALUE_DECL_SITE, LazyMode.SYNCHRONIZED),
        LAZY_CONSTANT(Variant.LAZY_CONSTANT, null);

        private final Variant variant;
        private final LazyMode mode;

        Configuration(Variant variant, LazyMode mode) {
            this.variant = variant;
            this.mode = mode;
        }

        Holder create(int seed) {
            return variant.create(seed, mode);
        }
    }

    private enum Variant {
        DIRECT {
            @Override
            Holder create(int seed, LazyMode mode) {
                return new ControlHolder(seed);
            }
        },
        CACHED_METHOD {
            @Override
            Holder create(int seed, LazyMode mode) {
                return new CachedMethodHolder(seed);
            }
        },
        LAZY_CACHE_DECL_SITE {
            @Override
            Holder create(int seed, LazyMode mode) {
                return switch (mode) {
                    case PLAIN -> new PlainLazyCacheDeclSiteHolder(seed);
                    case CAS -> new CasLazyCacheDeclSiteHolder(seed);
                    case SYNCHRONIZED -> new SynchronizedLazyCacheDeclSiteHolder(seed);
                };
            }
        },
        LAZY_VALUE_USE_SITE {
            @Override
            Holder create(int seed, LazyMode mode) {
                return new UseSiteHolder(seed, mode);
            }
        },
        LAZY_VALUE_DECL_SITE {
            @Override
            Holder create(int seed, LazyMode mode) {
                return new DeclSiteHolder(seed, mode);
            }
        },
        LAZY_CONSTANT {
            @Override
            Holder create(int seed, LazyMode mode) {
                return new LazyConstantHolder(seed);
            }
        };

        abstract Holder create(int seed, LazyMode mode);
    }

    public enum LazyMode {
        PLAIN,
        CAS,
        SYNCHRONIZED
    }

    public interface Holder {
        List<Integer> get();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static List<Integer> computeValues(int seed) {
        SplittableRandom random = new SplittableRandom(seed);
        ArrayList<Integer> values = new ArrayList<>(VALUE_COUNT);
        for (int i = 0; i < VALUE_COUNT; i++) {
            values.add(random.nextInt());
        }
        return values;
    }

    public static final class ControlHolder implements Holder {
        private final int seed;
        private List<Integer> value;

        ControlHolder(int seed) {
            this.seed = seed;
        }

        @Override
        public List<Integer> get() {
            List<Integer> value = this.value;
            if (value == null) {
                this.value = value = computeValues(seed);
            }
            return value;
        }
    }

    public static final class CachedMethodHolder implements Holder {
        private final int seed;

        CachedMethodHolder(int seed) {
            this.seed = seed;
        }

        @Override
        public cached List<Integer> get() {
            return computeValues(seed);
        }
    }

    public abstract static class LazyCacheDeclSiteHolder implements Holder {
        protected static final LazyCacheDeclSite<LazyCacheDeclSiteHolder, List<Integer>> CACHE =
                LazyCacheDeclSite.ofField(LazyCacheDeclSiteHolder.class, "value",
                        LazyCacheDeclSiteHolder::compute);

        private final int seed;
        private List<Integer> value;

        LazyCacheDeclSiteHolder(int seed) {
            this.seed = seed;
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }

    public static final class PlainLazyCacheDeclSiteHolder extends LazyCacheDeclSiteHolder {
        PlainLazyCacheDeclSiteHolder(int seed) {
            super(seed);
        }

        @Override
        public List<Integer> get() {
            return CACHE.get(this);
        }
    }

    public static final class CasLazyCacheDeclSiteHolder extends LazyCacheDeclSiteHolder {
        CasLazyCacheDeclSiteHolder(int seed) {
            super(seed);
        }

        @Override
        public List<Integer> get() {
            return CACHE.getVolatile(this);
        }
    }

    public static final class SynchronizedLazyCacheDeclSiteHolder extends LazyCacheDeclSiteHolder {
        SynchronizedLazyCacheDeclSiteHolder(int seed) {
            super(seed);
        }

        @Override
        public List<Integer> get() {
            return CACHE.getSynchronized(this, this);
        }
    }

    public static final class UseSiteHolder implements Holder {
        private final int seed;
        private final LazyValueUseSite<List<Integer>> value;

        UseSiteHolder(int seed, LazyMode mode) {
            this.seed = seed;
            value = LazyValueUseSite.of(switch (mode) {
                case PLAIN -> LazyValueUseSite.Policy.PLAIN;
                case CAS -> LazyValueUseSite.Policy.CAS;
                case SYNCHRONIZED -> LazyValueUseSite.Policy.ONCE;
            });
        }

        @Override
        public List<Integer> get() {
            return value.get(this, UseSiteHolder::compute);
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }

    public static final class DeclSiteHolder implements Holder {
        private final int seed;
        private final LazyValueDeclSite<DeclSiteHolder, List<Integer>> value;

        DeclSiteHolder(int seed, LazyMode mode) {
            this.seed = seed;
            value = LazyValueDeclSite.of(switch (mode) {
                case PLAIN -> LazyValueDeclSite.Policy.PLAIN;
                case CAS -> LazyValueDeclSite.Policy.CAS;
                case SYNCHRONIZED -> LazyValueDeclSite.Policy.ONCE;
            }, DeclSiteHolder::compute);
        }

        @Override
        public List<Integer> get() {
            return value.get(this);
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }

    public static final class LazyConstantHolder implements Holder {
        private final int seed;
        private final LazyConstant<List<Integer>> value;

        LazyConstantHolder(int seed) {
            this.seed = seed;
            value = LazyConstant.of(this::compute);
        }

        @Override
        public List<Integer> get() {
            return value.get();
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }
}
