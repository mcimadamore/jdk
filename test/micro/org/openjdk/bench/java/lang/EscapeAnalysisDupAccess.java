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

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 3, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class EscapeAnalysisDupAccess {

    private static final int ITERS = 10;

    private DirectFoo directSrc = new DirectFoo(new int[] { 1, 2, 3, 4 });
    private DirectFoo directDst = new DirectFoo(new int[] { 5, 6, 7, 8 });

    private Dup1Foo dup1Src = new Dup1Foo(new int[] { 1, 2, 3, 4 });
    private Dup1Foo dup1Dst = new Dup1Foo(new int[] { 5, 6, 7, 8 });

    private Dup2Foo dup2Src = new Dup2Foo(new int[] { 1, 2, 3, 4 });
    private Dup2Foo dup2Dst = new Dup2Foo(new int[] { 5, 6, 7, 8 });

    public record DirectFoo(int[] values) {
        int x() { return values[0]; }
        int y() { return values[1]; }
        int z() { return values[2]; }
        int w() { return values[3]; }

        DirectFoo x(int value) {
            values[0] = value;
            return this;
        }
        DirectFoo y(int value) {
            values[1] = value;
            return this;
        }
        DirectFoo z(int value) {
            values[2] = value;
            return this;
        }
        DirectFoo w(int value) {
            values[3] = value;
            return this;
        }

        int sum() { return values[0] + values[1] + values[2] + values[3]; }
    }

    public record Dup1Foo(int[] values) {
        Dup1Foo dup() { return new Dup1Foo(values); }

        int x() { return dup().values[0]; }
        int y() { return dup().values[1]; }
        int z() { return dup().values[2]; }
        int w() { return dup().values[3]; }

        Dup1Foo x(int value) {
            dup().values[0] = value;
            return this;
        }
        Dup1Foo y(int value) {
            dup().values[1] = value;
            return this;
        }
        Dup1Foo z(int value) {
            dup().values[2] = value;
            return this;
        }
        Dup1Foo w(int value) {
            dup().values[3] = value;
            return this;
        }

        int sum() { return values[0] + values[1] + values[2] + values[3]; }
    }

    public record Dup2Foo(int[] values) {
        Dup2Foo dup() { return new Dup2Foo(values); }

        private Dup2Foo twice() { return dup().dup(); }

        int x() { return twice().values[0]; }
        int y() { return twice().values[1]; }
        int z() { return twice().values[2]; }
        int w() { return twice().values[3]; }

        Dup2Foo x(int value) {
            twice().values[0] = value;
            return this;
        }
        Dup2Foo y(int value) {
            twice().values[1] = value;
            return this;
        }
        Dup2Foo z(int value) {
            twice().values[2] = value;
            return this;
        }
        Dup2Foo w(int value) {
            twice().values[3] = value;
            return this;
        }

        int sum() { return values[0] + values[1] + values[2] + values[3]; }
    }

    @Benchmark
    public int copyDirect() {
        DirectFoo d = directDst;
        DirectFoo s = directSrc;
        for (int i = 0; i < ITERS; i++) {
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
        return d.sum();
    }

    @Benchmark
    public int copyDup1() {
        Dup1Foo d = dup1Dst;
        Dup1Foo s = dup1Src;
        for (int i = 0; i < ITERS; i++) {
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
        return d.sum();
    }

    @Benchmark
    public int copyDup2() {
        Dup2Foo d = dup2Dst;
        Dup2Foo s = dup2Src;
        for (int i = 0; i < ITERS; i++) {
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d = d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
        return d.sum();
    }
}
