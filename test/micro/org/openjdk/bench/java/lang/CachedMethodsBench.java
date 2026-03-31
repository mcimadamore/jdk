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
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark for cached method steady-state access.
 * Synchronized variants are ordinary baselines, since cached methods do not
 * currently allow the synchronized modifier.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class CachedMethodsBench {

    private static final Targets targets = new Targets();

    @Benchmark
    public int staticConstantBaseline() {
        return Targets.staticConstant();
    }

    @Benchmark
    public int staticFieldBaseline() {
        return Targets.staticPlain();
    }

    @Benchmark
    public int staticCached() {
        return Targets.staticCached();
    }

    @Benchmark
    public int instanceConstantBaseline() {
        return targets.instanceConstant();
    }

    @Benchmark
    public int instanceFieldBaseline() {
        return targets.instancePlain();
    }

    @Benchmark
    public int instanceCached() {
        return targets.instanceCached();
    }

    static final class Targets {
        private static int staticValue = 42;

        private int instanceValue = 42;

        static int staticConstant() {
            return 42;
        }

        static int staticPlain() {
            return staticValue;
        }

        static cached int staticCached() {
            return staticValue;
        }

        int instanceConstant() {
            return 42;
        }

        int instancePlain() {
            return instanceValue;
        }

        cached int instanceCached() {
            return instanceValue;
        }
    }
}
