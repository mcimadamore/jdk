/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.snippet;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Snippets for the ComputedConstant class
 */
public final class ComputedConstantSnippets {

    private ComputedConstantSnippets() {
    }

    static final
    // @start region="DemoPreset"
    class DemoPreset {

        private static final ComputedConstant<Foo> FOO = ComputedConstant.of(Foo::new); // provider = Foo::new

        public Foo theFoo() {
            // Foo is constructed and recorded before the first invocation returns
            return FOO.get();
        }
    }
    // @end

    static final
    // @start region="DemoHolder"
    class DemoHolder {

        public Foo theBar() {
            class Holder {
                private static final Foo FOO = new Foo();
            }

            // Foo is lazily constructed and recorded here upon first invocation
            return Holder.FOO;
        }
    }
    // @end

    static final
    // @start region="DemoBackground"
    class DemoBackground {

        private static final ComputedConstant<Foo> CONSTANT = ComputedConstant.of(Foo::new);

        static {
            Thread.ofVirtual().start(CONSTANT::get);
        }

        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(1000);
            // CONSTANT is likely already pre-computed here by a background thread
            System.out.println(CONSTANT.get());
        }
    }
    // @end

    static final
    // @start region="SupplierDemo"
    class SupplierDemo {

        // Eager Supplier of Foo
        private static final Supplier<Foo> EAGER_FOO = Foo::new;

        // Turns an eager Supplier into a caching lazy Supplier
        private static final Supplier<Foo> LAZILY_CACHED_FOO = ComputedConstant.of(EAGER_FOO);

        public static void main(String[] args) {
            // Lazily construct and record the one-and-only Foo
            Foo theFoo = LAZILY_CACHED_FOO.get();
        }
    }
    // @end

    static final
    // @start region="DemoList"
    class DemoList {

        // 1. Declare a List of ComputedConstant elements of size 32
        private static final List<ComputedConstant<Long>> VALUE_PO2_CACHE =
                ComputedConstant.of(32, index -> 1L << index); // mappingProvider = index -> 1L << index

        public long powerOfTwo(int n) {
            // 2. The n:th slot is computed and bound here before
            //    the first call of get(n) returns. The other elements are not affected.
            // 3. Using an n outside the list will throw an IndexOutOfBoundsException
            return VALUE_PO2_CACHE.get(n).get();
        }
    }
    // @end

    static final
    // @start region="DemoNull"
    class DemoNull {

        private final Supplier<Optional<Color>> backgroundColor =
                ComputedConstant.of(() -> Optional.ofNullable(calculateBgColor()));

        Color backgroundColor(Color defaultColor) {
            return backgroundColor.get()
                    .orElse(defaultColor);
        }

        private Color calculateBgColor() {
            // Read background color from file returning "null" if it fails.
            // ...
            return null;
        }
    }
    // @end

    // Dummy classes
    static final class Foo {}
    static final class Color {}

}
