/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 0000000
 * @summary Exercise runtime handing of templated strings.
 * @enablePreview true
 */

import static java.lang.StringTemplate.str;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class Basic {
    public static void main(String... arg) {
        equalsHashCode();
        concatenationTests();
        componentTests();
        //limitsTests();
        stringTemplateCoverage();
        emptyExpressionTest();
        mapTests();
    }

    static void ASSERT(String a, String b) {
        if (!Objects.equals(a, b)) {
            System.out.println(a);
            System.out.println(b);
            throw new RuntimeException("Test failed");
        }
    }

    static void ASSERT(Object a, Object b) {
        if (!Objects.deepEquals(a, b)) {
            System.out.println(a);
            System.out.println(b);
            throw new RuntimeException("Test failed");
        }
    }

    /*
     * equals and hashCode tests.
     */
    static void equalsHashCode() {
        int x = 10;
        int y = 20;
        int a = 10;
        int b = 20;

        StringTemplate st0 = "\{x} + \{y} = \{x + y}";
        StringTemplate st1 = "\{a} + \{b} = \{a + b}";
        StringTemplate st2 = "\{x} + \{y} = \{x + y}!";
        x++;
        StringTemplate st3 = "\{x} + \{y} = \{x + y}";

        if (!st0.equals(st1)) throw new RuntimeException("st0 != st1");
        if (st0.equals(st2)) throw new RuntimeException("st0 == st2");
        if (st0.equals(st3)) throw new RuntimeException("st0 == st3");

        if (st0.hashCode() != st1.hashCode()) throw new RuntimeException("st0.hashCode() != st1.hashCode()");
    }

    /*
     * Concatenation tests.
     */
    static void concatenationTests() {
        int x = 10;
        int y = 20;

        ASSERT(str("\{x} \{y}"), x + " " + y);
        ASSERT(str("\{x + y}"), "" + (x + y));
    }

    /*
     * Component tests.
     */
    static void componentTests() {
        int x = 10;
        int y = 20;

        StringTemplate st = "\{x} + \{y} = \{x + y}";
        ASSERT(st.values(), List.of(x, y, x + y));
        ASSERT(st.fragments(), List.of("", " + ", " = ", ""));
        ASSERT(str(st), x + " + " + y + " = " + (x + y));
    }

//    /*
//     * Limits tests.
//     */
//    static void limitsTests() {
//        int x = 9;
//
//        StringTemplate ts250 = """
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             """;
//        ASSERT(ts250.values().size(), 250);
//        ASSERT(ts250.str(), """
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999
//               """);
//
//        StringTemplate ts251 = """
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}
//             """;
//        ASSERT(ts251.values().size(), 251);
//        ASSERT(ts251.str(), """
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9
//               """);
//
//        StringTemplate ts252 = """
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}
//             """;
//        ASSERT(ts252.values().size(), 252);
//        ASSERT(ts252.str(), """
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 99
//               """);
//
//        StringTemplate ts253 = """
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}
//             """;
//        ASSERT(ts253.values().size(), 253);
//        ASSERT(ts253.str(), """
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 999
//               """);
//
//        StringTemplate ts254 = """
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}
//             """;
//        ASSERT(ts254.values().size(), 254);
//        ASSERT(ts254.str(), """
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999
//               """);
//
//        StringTemplate ts255 = """
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}
//             """;
//        ASSERT(ts255.values().size(), 255);
//        ASSERT(ts255.str(), """
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 99999
//               """);
//
//        StringTemplate ts256 = """
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
//             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}
//             """;
//        ASSERT(ts256.values().size(), 256);
//        ASSERT(ts256.str(), """
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 9999999999
//
//               9999999999 9999999999
//               9999999999 9999999999
//               9999999999 999999
//               """);
//
//    }

    /*
     *  StringTemplate coverage
     */
    static void stringTemplateCoverage() {
//        StringTemplate tsNoValues = StringTemplate.of("No Values");
//
//        ASSERT(tsNoValues.values(), List.of());
//        ASSERT(tsNoValues.fragments(), List.of("No Values"));
//        ASSERT(tsNoValues.str(), "No Values");

        int x = 10, y = 20;
        StringTemplate src = "\{x} + \{y} = \{x + y}";
//        StringTemplate tsValues = StringTemplate.of(src.fragments(), src.values());
//        ASSERT(tsValues.fragments(), List.of("", " + ", " = ", ""));
//        ASSERT(tsValues.values(), List.of(x, y, x + y));
        //ASSERT(str(tsValues), x + " + " + y + " = " + (x + y));
        ASSERT(str(StringTemplate.combine(false, src, src)),
                str("\{x} + \{y} = \{x + y}\{x} + \{y} = \{x + y}"));
        ASSERT(StringTemplate.combine(false, src), src);
        ASSERT(str(StringTemplate.combine(false)), "");
        ASSERT(str(StringTemplate.combine(false, List.of(src, src))),
                str("\{x} + \{y} = \{x + y}\{x} + \{y} = \{x + y}"));
        ASSERT(StringTemplate.str(src), x + " + " + y + " = " + (x + y));
        ASSERT(str(t"a string"), "a string");
        StringTemplate color = "\{"red"}";
        StringTemplate shape = "\{"triangle"}";
        StringTemplate statement = "This is a \{color} \{shape}.";
        ASSERT(str(StringTemplate.combine(true, statement)), "This is a red triangle.");
    }

    /*
     *  Empty expression
     */
    static void emptyExpressionTest() {
        ASSERT("\{}".fragments().size(), 2);
        ASSERT("\{}".fragments().get(0), "");
        ASSERT("\{}".fragments().get(1), "");
        ASSERT("\{}".values().size(), 1);
        ASSERT("\{}".values().get(0), null);
    }

    /*
     *  mapFragments and mapValues
     */
    static void mapTests() {
        int x = 10, y = 20;
        StringTemplate st = "The sum of \{x} and \{y} equals \{x + y}";
        //StringTemplate st1 = st.mapFragments(String::toUpperCase);
        StringTemplate st2 = st.mapValues(v -> v instanceof Integer i ? i * 100 : v);
        //ASSERT(str(st1), "THE SUM OF 10 AND 20 EQUALS 30");
        ASSERT(str(st2), "The sum of 1000 and 2000 equals 3000");
    }




















}
