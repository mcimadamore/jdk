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
import java.lang.invoke.AbstractLazyValueDeclSite;
import java.lang.invoke.AbstractLazyValueUseSite;
import java.lang.invoke.LazyCache;
import java.lang.invoke.LazyCacheUseSite;
import java.lang.invoke.LazyValueDeclSite;
import java.lang.invoke.LazyValueUseSite;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import jdk.internal.misc.Unsafe;

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
        return createHolders(state.variant);
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

    private static Holder[] createHolders(Variant variant) {
        Holder[] holders = new Holder[HOLDER_COUNT];
        for (int i = 0; i < holders.length; i++) {
            holders[i] = variant.create(i);
        }
        return holders;
    }

    @State(Scope.Thread)
    public static class CreateState {
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
                holder.get();
            }
        }
    }

    // some control versions are commented out
    public enum Variant {
        DIRECT {
            @Override
            Holder create(int seed) {
                return new ControlHolder(seed);
            }
        },
//        DIRECT_ERASED {
//            @Override
//            Holder create(int seed) {
//                return new ErasedControlHolder(seed);
//            }
//        },
//        DIRECT_UNSAFE {
//            @Override
//            Holder create(int seed) {
//                return new UnsafeControlHolder(seed);
//            }
//        },
        LAZY_CACHE {
            @Override
            Holder create(int seed) {
                return new LazyCacheHolder(seed);
            }
        },
//        LAZY_CACHE_USE_SITE {
//            @Override
//            Holder create(int seed) {
//                return new LazyCacheUseSiteHolder(seed);
//            }
//        },
        LAZY_VALUE_USE_SITE {
            @Override
            Holder create(int seed) {
                return new UseSiteHolder(seed);
            }
        },
//        LAZY_VALUE_USE_SITE_ABSTRACT {
//            @Override
//            Holder create(int seed) {
//                return new AbstractUseSiteHolder(seed);
//            }
//        },
        LAZY_VALUE_DECL_SITE {
            @Override
            Holder create(int seed) {
                return new DeclSiteHolder(seed);
            }
        },
//        LAZY_VALUE_DECL_SITE_ABSTRACT {
//            @Override
//            Holder create(int seed) {
//                return new AbstractHolder(seed);
//            }
//        },
        LAZY_CONSTANT {
            @Override
            Holder create(int seed) {
                return new LazyConstantHolder(seed);
            }
        };

        abstract Holder create(int seed);
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

    public static final class LazyCacheHolder implements Holder {
        private static final LazyCache<LazyCacheHolder, List<Integer>> CACHE =
                LazyCache.ofField(LazyCacheHolder.class, "value", LazyCacheHolder::compute);

        private final int seed;
        private List<Integer> value;

        LazyCacheHolder(int seed) {
            this.seed = seed;
        }

        @Override
        public List<Integer> get() {
            return CACHE.get(this);
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }

    public static final class LazyCacheUseSiteHolder implements Holder {
        private static final LazyCacheUseSite<LazyCacheUseSiteHolder, List<Integer>> CACHE =
                LazyCacheUseSite.ofField(LazyCacheUseSiteHolder.class, "value",
                        LazyCacheUseSite.Policy.PLAIN);

        private final int seed;
        private List<Integer> value;

        LazyCacheUseSiteHolder(int seed) {
            this.seed = seed;
        }

        @Override
        public List<Integer> get() {
            return CACHE.get(this, LazyCacheUseSiteHolder::compute);
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }

    public static final class ErasedControlHolder implements Holder {
        private final int seed;
        private Object value;

        ErasedControlHolder(int seed) {
            this.seed = seed;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Integer> get() {
            Object value = this.value;
            if (value == null) {
                this.value = value = computeValues(seed);
            }
            return (List<Integer>) value;
        }
    }

    public static final class UnsafeControlHolder implements Holder {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET =
                UNSAFE.objectFieldOffset(UnsafeControlHolder.class, "value");

        private final int seed;
        private List<Integer> value;

        UnsafeControlHolder(int seed) {
            this.seed = seed;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Integer> get() {
            Object value = UNSAFE.getReference(this, VALUE_OFFSET);
            if (value == null) {
                value = computeValues(seed);
                UNSAFE.putReference(this, VALUE_OFFSET, value);
            }
            return (List<Integer>) value;
        }
    }

    public static final class UseSiteHolder implements Holder {
        private final int seed;
        private final LazyValueUseSite<List<Integer>> value =
                LazyValueUseSite.of(LazyValueUseSite.Policy.PLAIN);

        UseSiteHolder(int seed) {
            this.seed = seed;
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
        private final LazyValueDeclSite<DeclSiteHolder, List<Integer>> value =
                LazyValueDeclSite.of(LazyValueDeclSite.Policy.PLAIN, DeclSiteHolder::compute);

        DeclSiteHolder(int seed) {
            this.seed = seed;
        }

        @Override
        public List<Integer> get() {
            return value.get(this);
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }

    public static final class AbstractUseSiteHolder implements Holder {
        private final int seed;
        private final AbstractLazyValueUseSite<List<Integer>> value =
                AbstractLazyValueUseSite.of(AbstractLazyValueUseSite.Policy.PLAIN);

        AbstractUseSiteHolder(int seed) {
            this.seed = seed;
        }

        @Override
        public List<Integer> get() {
            return value.get(this, AbstractUseSiteHolder::compute);
        }

        private List<Integer> compute() {
            return computeValues(seed);
        }
    }

    public static final class AbstractHolder implements Holder {
        private static final class Value
                extends AbstractLazyValueDeclSite<AbstractHolder, List<Integer>> {
            @Override
            protected List<Integer> compute(AbstractHolder holder) {
                return computeValues(holder.seed);
            }
        }

        private final int seed;
        private final Value value = new Value();

        AbstractHolder(int seed) {
            this.seed = seed;
        }

        @Override
        public List<Integer> get() {
            return value.getPlain(this);
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
