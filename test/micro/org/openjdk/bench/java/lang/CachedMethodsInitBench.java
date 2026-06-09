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
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark cached methods against ordinary lazy fields for an immutable list
 * that is expensive enough to resemble typical library metadata. Each benchmark
 * invocation scans an array of targets; setup preinitializes the configured
 * fraction of targets to control the hit rate.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
public class CachedMethodsInitBench {

    private static final int TARGETS = 1000;

    @Benchmark
    @OperationsPerInvocation(TARGETS)
    public int cachedList(AccessState state) {
        int sum = 0;
        for (CachedTarget target : state.cached) {
            sum += target.list().get(0);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(TARGETS)
    public int manualList(AccessState state) {
        int sum = 0;
        for (ManualTarget target : state.manual) {
            sum += target.list().get(0);
        }
        return sum;
    }

    @State(Scope.Thread)
    public static class AccessState {
        @Param({"100", "99", "95", "90", "50", "0"})
        int hitRate;

        @Param({"1", "16", "128", "1024"})
        int initSize;

        CachedTarget[] cached;
        ManualTarget[] manual;

        @Setup(Level.Invocation)
        public void setupInvocation() {
            cached = new CachedTarget[TARGETS];
            manual = new ManualTarget[TARGETS];

            int hitCount = TARGETS * hitRate / 100;
            for (int i = 0; i < TARGETS; i++) {
                cached[i] = new CachedTarget(i, initSize);
                manual[i] = new ManualTarget(i, initSize);
                if (i < hitCount) {
                    cached[i].list();
                    manual[i].list();
                }
            }
        }
    }

    static final class CachedTarget {
        private final long seed;
        private final int initSize;

        CachedTarget(long seed, int initSize) {
            this.seed = seed;
            this.initSize = initSize;
        }

        cached List<Integer> list() {
            return buildList(seed, initSize);
        }
    }

    static final class ManualTarget {
        private final long seed;
        private final int initSize;
        private List<Integer> list;

        ManualTarget(long seed, int initSize) {
            this.seed = seed;
            this.initSize = initSize;
        }

        List<Integer> list() {
            List<Integer> list = this.list;
            if (list == null) {
                list = buildList(seed, initSize);
                this.list = list;
            }
            return list;
        }
    }

    private static List<Integer> buildList(long seed, int initSize) {
        SplittableRandom random = new SplittableRandom(seed);
        ArrayList<Integer> list = new ArrayList<>(initSize);
        for (int i = 0; i < initSize; i++) {
            list.add(random.nextInt());
        }
        return List.copyOf(list);
    }
}
