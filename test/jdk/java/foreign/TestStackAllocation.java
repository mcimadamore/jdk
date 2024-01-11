/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run testng/othervm TestStackAllocation
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.Stack;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TestStackAllocation {

    static final int[] elementSizes = { 1, 2, 4, 8 };

    static int elementSize(int index) {
        return elementSizes[index % elementSizes.length];
    }

    @Test(dataProvider = "stacks")
    public void testBasic(Stack stack) {
        try (Arena arena = stack.push()) {
            assertThrows(IllegalStateException.class, stack::close);
            for (int i = 0 ; i < 100 ; i++) {
                arena.allocate(elementSize(i), elementSize(i));
            }
        }
    }

    @Test(dataProvider = "stacks")
    public void testNested(Stack stack) {
        try (Arena arena1 = stack.push()) {
            assertThrows(IllegalStateException.class, stack::close);
            for (int i = 0 ; i < 100 ; i++) {
                arena1.allocate(elementSize(i), elementSize(i));
            }
            try (Arena arena2 = stack.push()) {
                assertThrows(IllegalStateException.class, stack::close);
                assertThrows(IllegalStateException.class, arena1::close);
                assertThrows(IllegalStateException.class, () -> arena1.allocate(10));
                for (int i = 0 ; i < 100 ; i++) {
                    arena2.allocate(elementSize(i), elementSize(i));
                }
                try (Arena arena3 = stack.push()) {
                    assertThrows(IllegalStateException.class, stack::close);
                    assertThrows(IllegalStateException.class, arena1::close);
                    assertThrows(IllegalStateException.class, arena2::close);
                    assertThrows(IllegalStateException.class, () -> arena1.allocate(10));
                    assertThrows(IllegalStateException.class, () -> arena2.allocate(10));
                    for (int i = 0 ; i < 100 ; i++) {
                        arena3.allocate(elementSize(i), elementSize(i));
                    }
                } // arena3 needs to be top here or it will throw!
            } // arena2 needs to be top here or it will throw!
        } // arena1 needs to be top here or it will throw!
    }

    @DataProvider(name = "stacks")
    static Object[][] stacks() {
        return new Object[][] {
                { Stack.newStack(20) },
                { Stack.newStack(40) },
                { Stack.newStack(80) },
                { Stack.newStack(100) },
        };
    }
}
